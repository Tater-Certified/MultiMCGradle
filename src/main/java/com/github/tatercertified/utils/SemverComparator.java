package com.github.tatercertified.utils;

import com.vdurmont.semver4j.Semver;

import java.util.Comparator;

public class SemverComparator implements Comparator<String> {
    @Override
    public int compare(String o1, String o2) {
        Semver o1Sem = new Semver(o1);
        Semver o2Sem = new Semver(o2);
        if (o1Sem.isLowerThan(o2Sem)) {
            return -1;
        } else if (o1Sem.isEqualTo(o2Sem)) {
            return 0;
        } else {
            return 1;
        }
    }
}
