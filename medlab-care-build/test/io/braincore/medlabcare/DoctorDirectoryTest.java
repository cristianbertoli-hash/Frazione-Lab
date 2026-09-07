package io.braincore.medlabcare;

import java.util.List;

public final class DoctorDirectoryTest {
    public static void main(String[] args) {
        List<DoctorDirectory.Doctor> carpi = DoctorDirectory.forLocation("Emilia-Romagna", "Modena", "Carpi");
        require(carpi.size() >= 40, "Carpi doctor directory should contain at least 40 public entries");
        require(contains(carpi, "Catalano Claudia"), "Catalano Claudia must be present");
        require(contains(carpi, "Erzili Elisa"), "Erzili Elisa must be present");
        require(contains(carpi, "Caffari Eugenia"), "Caffari Eugenia must be present");
        require(DoctorDirectory.forLocation("Emilia-Romagna", "Bologna", "Bologna").isEmpty(),
                "Unsupported areas must return an empty list, never invented data");
        System.out.println("DoctorDirectory tests: PASS (" + carpi.size() + " Carpi doctors)");
    }

    private static boolean contains(List<DoctorDirectory.Doctor> list, String name) {
        for (DoctorDirectory.Doctor d : list) if (name.equals(d.name)) return true;
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
