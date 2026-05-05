package com.realnocode.repository;

import com.realnocode.model.PageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PageHistoryRepository extends JpaRepository<PageHistory, Long> {

    List<PageHistory> findAllByOrderByCreatedAtDesc();
}
