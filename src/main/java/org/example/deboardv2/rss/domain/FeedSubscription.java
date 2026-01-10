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
