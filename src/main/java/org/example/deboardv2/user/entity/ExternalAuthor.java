package org.example.deboardv2.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class ExternalAuthor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String sourceUrl;

    public void update(String name, String sourceUrl) {
        this.name = name;
        this.sourceUrl = sourceUrl;
    }
}
