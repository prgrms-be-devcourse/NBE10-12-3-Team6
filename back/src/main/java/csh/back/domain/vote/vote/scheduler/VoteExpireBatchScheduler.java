package csh.back.domain.vote.vote.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoteExpireBatchScheduler {

    private final JobOperator jobOperator;
    private final Job expireVoteJob;

    // 매일 00:05 KST. 00:00 정각 아님 — expireTime==자정인 놈이랑 실행 시각 겹치는 race 회피.
    // (expireTime <= :now라 어차피 다 잡히지만 여유 5분)
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void runExpireVoteJob() {
        try {
            jobOperator.startNextInstance(expireVoteJob);
        } catch (Exception e) {
            log.error("expireVoteJob 실행 실패", e); // 스케줄 실패는 조용히 묻히지 않게 로깅
        }
    }
}