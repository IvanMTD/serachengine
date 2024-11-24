package searchengine.models;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;

@Data
@Entity
@NoArgsConstructor(force = true)
@Table(name = "lemma")
public class Lemma implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "lemma", columnDefinition = "VARCHAR(255)", length = 255, nullable = false)
    private String lemma;

    @Column(name = "frequency", nullable = false)
    private int frequency;
}
