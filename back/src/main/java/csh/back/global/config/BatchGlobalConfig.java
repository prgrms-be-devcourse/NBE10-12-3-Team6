package csh.back.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

@Slf4j
@Configuration
public class BatchGlobalConfig {


    // ---------------------------------------------------------
    //  1. 공통 스레드 풀 (병렬 Step이나 Multi-thread Chunk 처리에 사용)
    // ---------------------------------------------------------
    @Bean(name = "globalBatchTaskExecutor")
    public ThreadPoolTaskExecutor globalBatchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10); // 기본 대기 스레드 수
        executor.setMaxPoolSize(50);  // 최대 확장 스레드 수
        executor.setQueueCapacity(100); // 큐에서 대기할 수 있는 작업 수
        executor.setThreadNamePrefix("batch-thread-");
        // 애플리케이션 종료 시 진행 중인 작업이 끝날 때까지 대기 (Graceful Shutdown)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    // ---------------------------------------------------------
    //  2. 공통 Job 리스너 (모든 Job에 주입해서 실패/성공 여부 로깅 및 알림)
    // ---------------------------------------------------------
    @Bean
    public JobExecutionListener globalJobListener() {
        return new JobExecutionListener() {

            @Override
            public void beforeJob(JobExecution jobExecution) {
                log.info(" [배치 시작] Job Name: {}, 파라미터: {}",
                        jobExecution.getJobInstance().getJobName(),
                        jobExecution.getJobParameters());
            }

            @Override
            public void afterJob(JobExecution jobExecution) {
                long duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
                if (jobExecution.getStatus() == BatchStatus.FAILED) {
                    log.error(" [배치 실패] Job Name: {}, 걸린 시간: {}ms",
                            jobExecution.getJobInstance().getJobName(), duration);
                    //  실무에서는 보통 여기에 Slack/Teams API 호출 코드를 넣어서
                    //  배치가 터지면 개발팀 메신저로 알람이 오게 만듦
                } else if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                    log.info(" [배치 성공] Job Name: {}, 걸린 시간: {}ms",
                            jobExecution.getJobInstance().getJobName(), duration);
                }
            }
        };
    }

    // ---------------------------------------------------------
    //  3. 공통 고유 ID 생성기 (Job을 여러 번 강제 재실행할 때 필요)
    // ---------------------------------------------------------
    @Bean
    public RunIdIncrementer globalRunIdIncrementer() {
        // 스프링 배치는 동일한 파라미터로 Job을 두 번 실행하지 않음.
        // 강제로 계속 실행해야 하는 경우(주로 테스트나 수동 트리거) 이 빈을 Job 설정에 붙여줌.
        return new RunIdIncrementer();
    }
}