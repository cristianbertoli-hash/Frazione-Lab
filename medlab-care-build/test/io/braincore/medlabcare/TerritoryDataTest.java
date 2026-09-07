package io.braincore.medlabcare;

public final class TerritoryDataTest {
    public static void main(String[] args) {
        assertTrue(TerritoryData.CAU.size() == 45, "Expected 45 territorial entries in official ER directory snapshot");
        assertTrue(TerritoryData.provinces().size() == 9, "Expected 9 Emilia-Romagna provinces");
        assertTrue(TerritoryData.citiesForProvince("Modena").contains("Carpi"), "Carpi missing");
        assertTrue(TerritoryData.cauFor("Modena", "Carpi").size() == 1, "Carpi CAU lookup failed");
        TerritoryData.Facility carpi = TerritoryData.cauFor("Modena", "Carpi").get(0);
        assertTrue("059659111".equals(carpi.phone), "Carpi official phone mismatch");
        System.out.println("MEDLAB Care territory tests: PASS");
    }

    private static void assertTrue(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
