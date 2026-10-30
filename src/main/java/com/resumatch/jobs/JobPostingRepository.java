package com.resumatch.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPostingEntity, Long> {
    List<JobPostingEntity> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);
    List<JobPostingEntity> findByStatusOrderByCreatedAtDesc(com.resumatch.common.JobStatus status);

    @Query("select j from JobPostingEntity j where j.status='OPEN' and " +
           "(:location is null or lower(j.location) like lower(concat('%', :location, '%'))) and " +
           "(:title is null or lower(j.title) like lower(concat('%', :title, '%')))")
    List<JobPostingEntity> searchOpen(@Param("location") String location, @Param("title") String title);
}
