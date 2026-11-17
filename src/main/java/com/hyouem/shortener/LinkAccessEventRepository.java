package com.hyouem.shortener;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkAccessEventRepository
        extends JpaRepository<LinkAccessEvent, Long> {

    long countByLink_Id(Long linkId);
}