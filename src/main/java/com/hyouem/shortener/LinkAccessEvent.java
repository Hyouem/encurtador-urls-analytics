package com.hyouem.shortener;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "link_access_events")
public class LinkAccessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "link_id", nullable = false)
    private ShortLink link;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt = Instant.now();

    protected LinkAccessEvent() {
    }

    public LinkAccessEvent(ShortLink link) {
        this.link = link;
    }
}