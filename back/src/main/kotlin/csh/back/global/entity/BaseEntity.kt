package csh.back.global.entity

import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.Hibernate
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    final var id: Long? = null
        private set

    @CreatedDate
    final var createdAt: LocalDateTime? = null
        private set

    @LastModifiedDate
    final var updatedAt: LocalDateTime? = null
        private set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        val thisEffectiveClass = Hibernate.getClass(this)
        val otherEffectiveClass = Hibernate.getClass(other)
        if (thisEffectiveClass != otherEffectiveClass) return false
        other as BaseEntity
        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}