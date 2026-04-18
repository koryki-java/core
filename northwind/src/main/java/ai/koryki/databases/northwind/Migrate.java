package ai.koryki.databases.northwind;

import ai.koryki.scaffold.Util;
import ai.koryki.scaffold.domain.Model;

import java.io.File;

public class Migrate {

    public static void main(String[] args) {

        //northwind();


    }

//    private static void northwind() {
//        java.util.Locale en = java.util.Locale.ENGLISH;
//        Locale schema = Util.locale(NorthwindService.ROOT, en);
//        Model dm = Locale.deepCopy2(schema);
//
//        Util.write(dm, new File("northwind/build/model.json"));
//        Util.write(schema, new File("northwind/build/schema.json"));
//
//        Model model = Util.model(NorthwindService.ROOT, en);
//
//
//        java.util.Locale de = java.util.Locale.GERMAN;
//
//        schema = Util.locale(NorthwindService.ROOT, de);
//        dm = Locale.deepCopy2(schema);
//
//        Util.write(dm, new File("northwind/build/model_de.json"));
//
//        System.out.println();
//    }
}
