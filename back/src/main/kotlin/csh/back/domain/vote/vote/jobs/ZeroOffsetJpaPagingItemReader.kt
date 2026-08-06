package csh.back.domain.vote.vote.jobs

import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader

open class ZeroOffsetJpaPagingItemReader<T : Any>(entityManagerFactory: EntityManagerFactory) :
    JpaPagingItemReader<T>(entityManagerFactory) {

    override fun getPage(): Int = 0
}