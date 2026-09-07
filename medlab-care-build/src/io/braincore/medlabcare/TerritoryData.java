package io.braincore.medlabcare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TerritoryData {
    public static final String REGION_EMILIA_ROMAGNA = "Emilia-Romagna";
    public static final String OFFICIAL_CAU_DIRECTORY = "https://guidaservizi.fascicolo-sanitario.it/menu/cau";
    public static final String OFFICIAL_PS_DIRECTORY = "https://www.salute.gov.it/new/it/banche-dati/elenco-strutture-della-rete-dellemergenza-ospedaliera/";

    public static final class Facility {
        public final String province;
        public final String city;
        public final String name;
        public final String phone;
        public final String address;
        public final String officialUrl;

        public Facility(String province, String city, String name, String phone, String address, String officialUrl) {
            this.province = province;
            this.city = city;
            this.name = name;
            this.phone = phone == null ? "" : phone;
            this.address = address == null ? "" : address;
            this.officialUrl = officialUrl == null ? OFFICIAL_CAU_DIRECTORY : officialUrl;
        }
    }

    private TerritoryData() {}

    private static Facility f(String p, String c, String n) {
        return new Facility(p, c, n, "", "", OFFICIAL_CAU_DIRECTORY);
    }

    public static final List<Facility> CAU = Arrays.asList(
        f("Piacenza", "Podenzano", "Centro Assistenza e Urgenza (CAU)"),
        f("Piacenza", "Bobbio", "Centro Assistenza e Urgenza (CAU)"),
        f("Piacenza", "Fiorenzuola d'Arda", "Centro Assistenza e Urgenza (CAU)"),
        f("Piacenza", "Piacenza", "Centro Assistenza e Urgenza CAU"),
        f("Parma", "Langhirano", "Centro di Assistenza e Urgenza (CAU)"),
        f("Parma", "Fidenza", "Centro di assistenza e urgenza - CAU"),
        f("Parma", "Parma", "Centro di assistenza e urgenza CAU"),
        f("Reggio Emilia", "Reggio Emilia", "Centro di Assistenza e Urgenza CAU"),
        f("Reggio Emilia", "Correggio", "Centro di Assistenza e Urgenza CAU"),
        f("Reggio Emilia", "Scandiano", "Centro di Assistenza e Urgenza CAU"),
        f("Modena", "Finale Emilia", "Centro di assistenza e urgenza CAU"),
        new Facility("Modena", "Carpi", "Centro di assistenza e urgenza CAU – Carpi",
                "059659111", "Via Guido Molinari 2, Carpi",
                "https://www.ausl.mo.it/luogo/centro-di-assistenza-e-urgenza-cau-carpi/"),
        f("Modena", "Castelfranco Emilia", "Centro di assistenza e urgenza CAU"),
        f("Modena", "Fanano", "Centro di assistenza e urgenza CAU"),
        f("Modena", "Modena", "Centro di assistenza e urgenza CAU"),
        f("Bologna", "San Lazzaro di Savena", "Centro di Assistenza e Urgenza CAU"),
        f("Bologna", "Vergato", "Centro di Assistenza e Urgenza CAU"),
        f("Bologna", "Budrio", "Centro di Assistenza e Urgenza CAU"),
        f("Bologna", "Casalecchio di Reno", "Centro di Assistenza e Urgenza CAU"),
        f("Bologna", "Bologna", "Centro di Assistenza e Urgenza CAU"),
        f("Bologna", "Imola", "Centro di Assistenza e Urgenza CAU Imola"),
        f("Ferrara", "Ferrara", "Centro di Assistenza e Urgenza CAU"),
        f("Ferrara", "Comacchio", "Centro di Assistenza e Urgenza CAU"),
        f("Ferrara", "Copparo", "Centro di Assistenza e Urgenza CAU"),
        f("Ferrara", "Portomaggiore", "Centro di Assistenza e Urgenza CAU"),
        f("Ferrara", "Bondeno", "Centro di Assistenza e Urgenza CAU"),
        f("Ravenna", "Faenza", "Ambulatorio AFT"),
        f("Ravenna", "Cervia", "Centro di Assistenza e Urgenza CAU"),
        f("Ravenna", "Lugo", "Centro di Assistenza e Urgenza CAU"),
        f("Ravenna", "Ravenna", "Centro di Assistenza e Urgenza CAU"),
        f("Ravenna", "Conselice", "Centro di Assistenza e Urgenza CAU"),
        f("Ravenna", "Castel Bolognese", "Centro di Assistenza e Urgenza CAU"),
        f("Forlì-Cesena", "Savignano sul Rubicone", "Ambulatorio AFT"),
        f("Forlì-Cesena", "Mercato Saraceno", "Centro di Assistenza e Urgenza CAU"),
        f("Forlì-Cesena", "Bagno di Romagna", "Centro di Assistenza e Urgenza CAU"),
        f("Forlì-Cesena", "Cesenatico", "Centro di Assistenza e Urgenza CAU"),
        f("Forlì-Cesena", "Santa Sofia", "Centro di Assistenza e Urgenza CAU"),
        f("Forlì-Cesena", "Forlì", "Centro di Assistenza e Urgenza CAU"),
        f("Forlì-Cesena", "Cesena", "Centro di Assistenza e Urgenza CAU"),
        f("Rimini", "Cattolica", "Centro di Assistenza e Urgenza CAU"),
        f("Rimini", "Bellaria-Igea Marina", "Centro di Assistenza e Urgenza CAU"),
        f("Rimini", "Rimini", "Centro di Assistenza e Urgenza CAU"),
        f("Rimini", "Santarcangelo di Romagna", "Centro di Assistenza e Urgenza CAU"),
        f("Rimini", "Riccione", "Centro di Assistenza e Urgenza CAU"),
        f("Rimini", "Novafeltria", "Centro di Assistenza e Urgenza CAU")
    );

    public static List<String> provinces() {
        Set<String> s = new LinkedHashSet<>();
        for (Facility f : CAU) s.add(f.province);
        return new ArrayList<>(s);
    }

    public static List<String> citiesForProvince(String province) {
        Set<String> s = new LinkedHashSet<>();
        s.add("Tutti / altro comune");
        for (Facility f : CAU) if (f.province.equals(province)) s.add(f.city);
        return new ArrayList<>(s);
    }

    public static List<Facility> cauFor(String province, String city) {
        List<Facility> out = new ArrayList<>();
        boolean all = city == null || city.startsWith("Tutti");
        for (Facility f : CAU) {
            if (f.province.equals(province) && (all || f.city.equals(city))) out.add(f);
        }
        return out;
    }
}
