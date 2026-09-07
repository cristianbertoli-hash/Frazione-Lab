package io.braincore.medlabcare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DoctorDirectory {
    public static final String SOURCE_MODENA =
            "https://www.ausl.mo.it/strutture/medici-di-medicina-generale/elenco/";

    public static final class Doctor {
        public final String name;
        public final String address;
        public final String phone;
        public final String sourceUrl;

        Doctor(String name, String address, String phone, String sourceUrl) {
            this.name = name;
            this.address = address;
            this.phone = phone == null ? "" : phone;
            this.sourceUrl = sourceUrl;
        }

        @Override public String toString() {
            return name + " — " + address;
        }
    }

    private DoctorDirectory() {}

    public static List<Doctor> forLocation(String region, String province, String city) {
        if (!"Emilia-Romagna".equals(region) || !"Modena".equals(province) || !"Carpi".equals(city)) {
            return Collections.emptyList();
        }
        List<Doctor> out = new ArrayList<>();
        add(out, "Alessio Anna", "Piazzale Allende 1, Carpi", "");
        add(out, "Altomare Marinella", "via Cesare Beccaria n. 3/A, Carpi", "");
        add(out, "Andreoli Cristina", "via Cesare Beccaria n. 3/A, Carpi", "");
        add(out, "Bejgana Sanaa", "via Abram Lincoln n. 2/1, Carpi", "");
        add(out, "Bellodi Claudia", "via Mozart n. 3/D, Carpi", "");
        add(out, "Bertacchini Barbara", "via Vasco da Gama n. 28, Carpi", "");
        add(out, "Bombarda Mareika", "via Abram Lincoln n. 2/1, Carpi", "");
        add(out, "Boni Umberto", "via Cesare Beccaria n. 3/A, Carpi", "");
        add(out, "Bordoni Patrizia", "via Pezzana n. 82/B, Carpi", "");
        add(out, "Caffari Eugenia", "Piazzale Allende 1, Carpi", "059651938");
        add(out, "Calzarossa Lusardi Clara", "via Vasco da Gama n. 28, Carpi", "");
        add(out, "Carvelli Graziano", "via Roosevelt n. 41, Carpi", "");
        add(out, "Catalano Claudia", "Via Torino n. 3, Carpi", "0594346349");
        add(out, "Cavazzuti Matteo", "via Cesare Beccaria n. 3/A, Carpi", "");
        add(out, "Colonna Annalisa", "Via Pezzana 82/B, Carpi", "");
        add(out, "Conventi Riccardo", "via L. Spallanzani n. 12, Carpi", "");
        add(out, "Corrado Domenico Mario", "via Roosevelt n. 41, Carpi", "");
        add(out, "Costa Enea", "via Carpi Ravarino n. 1986, frazione Sozzigalli, Carpi", "");
        add(out, "Culzoni Francesca", "via Vasco da Gama n. 28, Carpi", "");
        add(out, "Currieri Virginia", "Piazzale Allende 1, Carpi", "");
        add(out, "Danini Alessandra", "via Pezzana n. 82/B, Carpi", "");
        add(out, "Erzili Elisa", "via Torino n. 3, Carpi", "0596230413");
        add(out, "Forte Gabriele", "via Roosevelt n. 41, Carpi", "");
        add(out, "Galati Giuseppe", "Piazzale Allende 1, Carpi", "");
        add(out, "Gallini Giulia", "Via Pezzana n. 82/B, Carpi", "");
        add(out, "Grassi Maria Elena", "via Vasco da Gama n. 28, Carpi", "");
        add(out, "Gualdi Gianluca", "via Mozart n. 3/D, Carpi", "");
        add(out, "Ingratta Mariadele", "Piazzale Allende 1, Carpi", "");
        add(out, "Lo Conte Giuseppe", "via Torino n. 3, Carpi", "");
        add(out, "Losi Andrea", "via Mar Ligure n. 1, frazione di Fossoli, Carpi", "");
        add(out, "Lugli Roberta", "via Pezzana n. 82/B, Carpi", "");
        add(out, "Magnani Emanuela", "via Pezzana n. 82/B, Carpi", "");
        add(out, "Mazzali Lorenzo", "via Mozart n. 3/D, Carpi", "");
        add(out, "Pace Maria Incoronata", "via Roosevelt n. 41, Carpi", "");
        add(out, "Pasquini Giovanni", "via Roosevelt n. 41, Carpi", "");
        add(out, "Prandi Barbara", "via Mar Ligure n. 1, frazione di Fossoli, Carpi", "");
        add(out, "Sacchetti Lara", "Via Spallanzani n. 12, Carpi", "");
        add(out, "Salami Irene", "via Vasco da Gama n. 28, Carpi", "");
        add(out, "Singh Jobanpreet", "Via Pezzana 82/B, Carpi", "");
        add(out, "Somo Ngalegah Mbo Guy Marcel", "via Spallanzani n. 12, Carpi", "");
        add(out, "Ternetti Andrea", "via Cesare Beccaria n. 3/A, Carpi", "");
        add(out, "Vanzini Cristiana", "via Beccaria n. 3/A, Carpi", "");
        add(out, "Vasta Alessandro", "Via Torino n. 3, Carpi", "");
        add(out, "Ventouri Vasiliki", "Piazzale Allende 1, Carpi", "");
        add(out, "Vignoli Annalisa", "via Mozart n. 3/D, Carpi", "");
        return Collections.unmodifiableList(out);
    }

    private static void add(List<Doctor> list, String name, String address, String phone) {
        list.add(new Doctor(name, address, phone, SOURCE_MODENA));
    }
}
