package csh.back.domain.trip.post.repository;

import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByTimeLineTripGroupId(Long tripId);

    List<Post> findByAuthorId(Long authorId);

    List<Post> findByAuthorIdInOrderByCreatedAtAsc(List<Long> authorIds);

    List<Post> findAllByAuthorId(Long authorId);

    @Query("""
        SELECT p FROM Post p
        LEFT JOIN FETCH p.timeLine t
        LEFT JOIN FETCH t.confirmedPlace
        WHERE p.author IN :members
        ORDER BY p.createdAt ASC
        """)
    List<Post> findWithTimeLineAndPlaceByAuthorIdIn(List<TripMember> members);

    List<Post> findByAuthorIdAndCreatedAtBetween(Long memberId, LocalDateTime startTime, LocalDateTime endTime);
}
