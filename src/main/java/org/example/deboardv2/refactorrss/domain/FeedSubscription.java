package org.example.deboardv2.refactorrss.domain;

import jakarta.persistence.*;
import org.example.deboardv2.user.entity.User;

@Entity
public class FeedSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customName;
    @ManyToOne
    private Feed feed;
    @ManyToOne
    private User user;

}
