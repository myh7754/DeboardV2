package org.example.deboardv2.likes.entity;

import jakarta.persistence.*;

@Entity
public class Likes {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "likes_id")
    private Long id;
}
