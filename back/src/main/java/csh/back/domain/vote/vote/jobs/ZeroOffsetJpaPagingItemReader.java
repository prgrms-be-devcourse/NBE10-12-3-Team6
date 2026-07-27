package csh.back.domain.vote.vote.jobs;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;

public class ZeroOffsetJpaPagingItemReader<T> extends JpaPagingItemReader<T> {
    public ZeroOffsetJpaPagingItemReader(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    public int getPage() {
        return 0; // page++는 내부에서 돌지만 실제 쿼리는 매번 OFFSET 0
    }
}