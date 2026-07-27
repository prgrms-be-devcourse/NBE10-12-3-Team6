package csh.back.domain.trip.group.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.member.entity.QTripMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//Q클래스 생성
import static csh.back.domain.trip.group.entity.QTripGroup.tripGroup;
import static csh.back.domain.trip.member.entity.QTripMember.tripMember;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

//2. 쿼리 구연체 파트
@Slf4j
@RequiredArgsConstructor
public class TripGroupRepositoryImpl implements TripGroupRepositoryCustom {
	private final JPAQueryFactory jpaQueryFactory;

	@Override
	public List<TripGroup> findAllByMemberIdWithSearch(Long memberId, String keyword, String startDate) {

		QTripMember me = new QTripMember("me"); //로그인 한 사용자가 속한 그룹 필터용
		QTripMember groupMember = new QTripMember("groupMember"); //그룹 내 맴버 필터용

		return jpaQueryFactory
				.selectFrom(tripGroup)
				.join(me).on(me.tripGroup.eq(tripGroup))
				.leftJoin(groupMember).on(groupMember.tripGroup.eq(tripGroup))
				.where(
						me.member.id.eq(memberId),
						keywordSearch(keyword, groupMember),
						dateSearch(startDate)
				)
				.orderBy(tripGroup.startDate.desc())
				.distinct()
				.fetch();
	}

	private BooleanExpression keywordSearch(String keyword, QTripMember groupMember) {
		if(!hasText(keyword)) return null;

		return tripGroup.name.containsIgnoreCase(keyword)
				.or(tripGroup.region.containsIgnoreCase(keyword))
				.or(groupMember.member.name.containsIgnoreCase(keyword));
	}

	private BooleanExpression dateSearch(String startDate) {
		return hasText(startDate) ? tripGroup.startDate.stringValue().containsIgnoreCase(startDate) : null;
	}
}
