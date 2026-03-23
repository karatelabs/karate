package io.karatelabs.core.interop;

import java.util.ArrayList;
import java.util.List;

/**
 * Cat POJO for testing Java interop, including nested collections.
 */
public class Cat {

    private int id;
    private String name;
    private List<Cat> kittens;

    public void addKitten(Cat kitten) {
        if (kittens == null) {
            kittens = new ArrayList<>();
        }
        kittens.add(kitten);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Cat> getKittens() {
        return kittens;
    }

    public void setKittens(List<Cat> kittens) {
        this.kittens = kittens;
    }

}
