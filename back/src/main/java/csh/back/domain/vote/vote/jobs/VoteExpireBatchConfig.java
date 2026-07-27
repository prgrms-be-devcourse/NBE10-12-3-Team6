package csh.back.domain.vote.vote.jobs;

import csh.back.domain.trip.timeline.service.TimeLineService;
import csh.back.domain.vote.vote.entity.Vote;
import csh.back.domain.vote.vote.enums.VoteStatus;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Configuration
public class VoteExpireBatchConfig {
    private final int CHUNK_SIZE = 100;

    // ── Reader: offset-0 고정 페이징 ──────────────────────────
    @StepScope
    @Bean
    public ZeroOffsetJpaPagingItemReader<Vote> expireVoteReader(EntityManagerFactory emf) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        ZeroOffsetJpaPagingItemReader<Vote> reader = new ZeroOffsetJpaPagingItemReader<>(emf);
        reader.setName("expireVoteReader");
        reader.setPageSize(CHUNK_SIZE); // ★ pageSize == chunkSize
        reader.setQueryString("""
                SELECT v FROM Vote v
                WHERE v.status = :pending
                  AND v.expireTime <= :now
                ORDER BY v.id
                """);
        reader.setParameterValues(Map.of(
                "pending", VoteStatus.PENDING,
                "now", now
        ));
        return reader;
    }

    // ── Writer: 서비스에 위임 (id만 넘김) ─────────────────────
    @Bean
    public ItemWriter<Vote> expireVoteWriter(TimeLineService timeLineService) {
        return chunk -> {
            for (Vote v : chunk) {
                timeLineService.expireAndConfirmBySystem(v.getId());
            }
        };
    }

    // ── Step ─────────────────────────────────────────────────
    @Bean
    public Step expireVoteStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               ZeroOffsetJpaPagingItemReader<Vote> expireVoteReader,
                               ItemWriter<Vote> expireVoteWriter) {

        return new StepBuilder("expireVoteStep", jobRepository)
                .<Vote, Vote>chunk(CHUNK_SIZE)              // ★ size만
                .transactionManager(transactionManager)     // ★ tx는 분리 호출
                .reader(expireVoteReader)
                .writer(expireVoteWriter)                   // processor 없음 (읽고 바로 처리)
                .build();
    }

    // ── Job ──────────────────────────────────────────────────
    @Bean
    public Job expireVoteJob(JobRepository jobRepository,
                             Step expireVoteStep,
                             RunIdIncrementer globalRunIdIncrementer) { // 기존 GlobalConfig 재사용

        return new JobBuilder("expireVoteJob", jobRepository)
                .incrementer(globalRunIdIncrementer)
                .start(expireVoteStep)
                .build();
    }
}