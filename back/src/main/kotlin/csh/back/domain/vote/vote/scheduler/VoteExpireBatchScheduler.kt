package csh.back.domain.vote.vote.scheduler

import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.launch.JobOperator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class VoteExpireBatchScheduler(
    private val jobOperator: JobOperator,
    private val expireVoteJob: Job
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 매일 00:05 KST. 00:00 정각 아님 — expireTime==자정인 놈이랑 실행 시각 겹치는 race 회피.
    // (expireTime <= :now라 어차피 다 잡히지만 여유 5분)
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    fun runExpireVoteJob() {
        try {
            jobOperator.startNextInstance(expireVoteJob)
        } catch (e: Exception) {
            log.error("expireVoteJob 실행 실패", e) // 스케줄 실패는 조용히 묻히지 않게 로깅
        }
    }
}