package ai.koryki.snowflake.covid19;

import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlRenderer;
import ai.koryki.jdbc.*;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.catalog.Util;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;

public class Covid19Service<P extends ResultConsumer<HeaderInfo>> {
    public static final String ROOT = "/ai/koryki/snowflake/databases/covid19/i18n";

    private final Engine<HeaderInfo, P> serivce;

    /**
     * Create Service with Locale.ENGLISH, not system default Locale!
     */
    public Covid19Service(Database<P> database, SqlRenderer renderer) {
        this(database, renderer, java.util.Locale.ENGLISH);
    }

    public Covid19Service(Database<P> database, SqlRenderer renderer, java.util.Locale locale) {

        this(database, renderer, resolver(locale));
    }

    public Covid19Service(Database<P> database, SqlRenderer renderer, LinkResolver resolver) {
        serivce = Engine.build(database, resolver, renderer);
    }

    public static LinkResolver resolver() {
        return resolver(java.util.Locale.ENGLISH);
    }

    public static LinkResolver resolver(java.util.Locale locale) {

        Schema db = Util.db(ROOT);
        Model schema = Util.model(ROOT, locale);
        LinkResolver resolver = new LinkResolver(locale, db, schema, true);
        return resolver;
    }

    public Engine<HeaderInfo, P> getEngine() {
        return serivce;
    }

}
