package org.example.deboardv2.rss.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deboardv2.user.entity.User;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Table(name = "feed_subscription")
public class FeedSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customName;
    @ManyToOne
    @JoinColumn(name = "feed_id", nullable = false)
    private Feed feed;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

}
