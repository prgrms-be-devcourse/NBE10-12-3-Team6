package csh.back.domain.trip.timeline.entity;

import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//타임라인 엔티티 -> 여행 중 사진 첨부
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "trip_timelines")
public class TimeLine extends BaseEntity {

    //최소 일차
    private static final int MINIMUM_DAY = 1;

    //FK
    //Join tripGroup Table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripGroup tripGroup;

    //FK
    //Join TripPlace Table
    //시간 카테고리를 처음 만들 땐 확정 장소가 없기에
    //nullable을 true로 변경
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_place_id", nullable = true)
    private TripPlace confirmedPlace;

    //이 타임라인이 몇 일차에 속하는 지
    private int dayNumber;

    //시작 시간
    private LocalDateTime startTime;

    //끝난 시간
    private LocalDateTime endTime;

    //생성자
    //빌드 사용
    @Builder
    private TimeLine(TripGroup tripGroup, int dayNumber, LocalDateTime startTime, LocalDateTime endTime) {

        validateTimeLine(tripGroup, dayNumber, startTime, endTime);

        this.tripGroup = tripGroup;
        this.dayNumber = dayNumber;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    //타임라인 생성에 필요한 기본값 검증
    private void validateTimeLine(TripGroup tripGroup, int dayNumber, LocalDateTime startTime, LocalDateTime endTime) {
        validateTripGroup(tripGroup);
        validateDayNumber(dayNumber);
        validateTimeRange(startTime, endTime);
    }

    //여행 모임이 제대로 들어오는 지 검증하는 메서드
    private void validateTripGroup(TripGroup tripGroup) {
        if (tripGroup == null) {
            throw new IllegalArgumentException("여행 모임은 필수입니다.");
        }
    }

    //dayNumber가 1일차 이상인지 -> 0 이하이면 막는 메서드
    private void validateDayNumber(int dayNumber) {
        if (dayNumber < MINIMUM_DAY) {
            throw new IllegalArgumentException("일차는 " + MINIMUM_DAY + " 이상이어야 합니다.");
        }
    }

    //endTime이 startTime보다 늦는지 확인하는 메서드 (엔티티 값 검증)
    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("시작 시간과 종료 시간은 필수입니다.");
        //startTime이 endTime보다 늦을 경우
        }else if (!endTime.isAfter(startTime)) {
            //막음
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }
    }

    //시간 수정 메서드 (엔티티 값 수정)
    public void updateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        validateTimeRange(startTime, endTime);
        this.startTime = startTime;
        this.endTime = endTime;
    }

    //확정 장소 반영 메서드
    public void updateConfirmedPlace(TripPlace confirmedPlace) {
        //null 검증
        if (confirmedPlace == null) {
            throw new IllegalArgumentException("확정할 장소가 없습니다.");
        }
        //null이 아닐 경우 값을 넣음
        this.confirmedPlace = confirmedPlace;
    }
}
