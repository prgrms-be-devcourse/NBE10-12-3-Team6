package csh.back.domain.vote.vote.jobs

import csh.back.domain.trip.timeline.service.TimelineService
import csh.back.domain.vote.vote.entity.Vote
import csh.back.domain.vote.vote.enums.VoteStatus
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.parameters.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime
import java.time.ZoneId

@Configuration
class VoteExpireBatchConfig {
    private val chunkSize = 100

    // ── Reader: offset-0 고정 페이징 ──────────────────────────
    @StepScope
    @Bean
    fun expireVoteReader(emf: EntityManagerFactory): ZeroOffsetJpaPagingItemReader<Vote> {
        val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))

        val reader = ZeroOffsetJpaPagingItemReader<Vote>(emf)
        reader.setName("expireVoteReader")
        reader.setPageSize(chunkSize) // ★ pageSize == chunkSize
        reader.setQueryString(
            """
            SELECT v FROM Vote v
            WHERE v.status = :pending
              AND v.expireTime <= :now
            ORDER BY v.id
            """.trimIndent()
        )
        reader.setParameterValues(
            mapOf(
                "pending" to VoteStatus.PENDING,
                "now" to now
            )
        )
        return reader
    }

    // ── Writer: 서비스에 위임 (id만 넘김) ─────────────────────
    @Bean
    fun expireVoteWriter(timeLineService: TimelineService): ItemWriter<Vote> {
        return ItemWriter { chunk ->
            for (v in chunk) {
                timeLineService.expireAndConfirmBySystem(v.id!!)
            }
        }
    }

    // ── Step ─────────────────────────────────────────────────
    @Bean
    fun expireVoteStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        expireVoteReader: ZeroOffsetJpaPagingItemReader<Vote>,
        expireVoteWriter: ItemWriter<Vote>
    ): Step {
        return StepBuilder("expireVoteStep", jobRepository)
            .chunk<Vote, Vote>(chunkSize) // ★ size만
            .transactionManager(transactionManager) // ★ tx는 분리 호출
            .reader(expireVoteReader)
            .writer(expireVoteWriter) // processor 없음 (읽고 바로 처리)
            .build()
    }

    // ── Job ──────────────────────────────────────────────────
    @Bean
    fun expireVoteJob(
        jobRepository: JobRepository,
        expireVoteStep: Step,
        globalRunIdIncrementer: RunIdIncrementer // 기존 GlobalConfig 재사용
    ): Job {
        return JobBuilder("expireVoteJob", jobRepository)
            .incrementer(globalRunIdIncrementer)
            .start(expireVoteStep)
            .build()
    }
}