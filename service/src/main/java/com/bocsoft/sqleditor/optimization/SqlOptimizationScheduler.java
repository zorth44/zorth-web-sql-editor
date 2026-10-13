package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.optimization.persistence.OptimizationAttempt;
import com.bocsoft.sqleditor.optimization.persistence.OptimizationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/** Local permits precede durable claim; restart discovers the database queue afresh. */
@Component
public class SqlOptimizationScheduler {
    private final OptimizationProperties properties;private final OptimizationRepository repository;private final SqlOptimizationWorker worker;private final Clock clock;private final MeterRegistry metrics;
    private final String workerId=UUID.randomUUID().toString();
    private final ScheduledExecutorService maintenance=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"optimization-scheduler");t.setDaemon(true);return t;});
    private final ExecutorService workers;private final Semaphore slots;private final AtomicBoolean closed=new AtomicBoolean();
    public SqlOptimizationScheduler(OptimizationProperties properties,OptimizationRepository repository,SqlOptimizationWorker worker,Clock clock,MeterRegistry metrics){
        this.properties=properties;this.repository=repository;this.worker=worker;this.clock=clock;this.metrics=metrics;
        int size=Math.max(1,properties.getWorkerConcurrency());slots=new Semaphore(size);workers=Executors.newFixedThreadPool(size,r->{Thread t=new Thread(r,"optimization-worker");t.setDaemon(true);return t;});
    }
    @PostConstruct public void start(){
        if(properties.isSchedulerEnabled())maintenance.scheduleWithFixedDelay(()->{
            try{tick();}catch(Exception ignored){metrics.counter("sql.optimization.scheduler.errors").increment();}
        },0,2000,TimeUnit.MILLISECONDS);
    }
    public void tick(){
        if(closed.get())return;
        repository.maintain(clock.instant(),properties.getAttemptBudgetSeconds());
        metrics.gauge("sql.optimization.queued",repository,r->r.queued());metrics.gauge("sql.optimization.running",repository,r->r.running());
        metrics.gauge("sql.optimization.oldest.wait.seconds",repository,r->r.oldestWait(clock.instant()));
        if(!properties.isEnabled()||!properties.isIntegrationReady())return;
        while(!closed.get() && slots.tryAcquire()){
            try{
                OptimizationAttempt attempt=repository.claim(clock.instant(),properties.getAttemptBudgetSeconds(),properties.getLeaseSeconds(),workerId);
                if(attempt==null){slots.release();break;}
                workers.execute(()->{try{worker.execute(attempt);}finally{slots.release();}});
            }catch(RuntimeException e){slots.release();throw e;}
        }
    }
    @PreDestroy public void close(){closed.set(true);maintenance.shutdownNow();workers.shutdownNow();}
}
