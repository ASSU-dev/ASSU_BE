package com.assu.server.domain.student.entity;

import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.store.entity.Store;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeCuration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "featured_store_id")
    private Store featuredStore;

    @Column(length = 255)
    private String featuredDiscountContent;

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "homeCuration", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HomeCurationItem> items = new ArrayList<>();

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateFeatured(Store featuredStore, String featuredDiscountContent) {
        this.featuredStore = featuredStore;
        this.featuredDiscountContent = featuredDiscountContent;
    }
}
