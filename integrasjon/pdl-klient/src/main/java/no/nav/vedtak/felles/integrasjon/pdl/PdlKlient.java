package no.nav.vedtak.felles.integrasjon.pdl;


import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kobylynskyi.graphql.codegen.model.graphql.GraphQLError;
import com.kobylynskyi.graphql.codegen.model.graphql.GraphQLRequest;
import com.kobylynskyi.graphql.codegen.model.graphql.GraphQLResult;

import no.nav.pdl.HentIdenterBolkQueryRequest;
import no.nav.pdl.HentIdenterBolkQueryResponse;
import no.nav.pdl.HentIdenterBolkResult;
import no.nav.pdl.HentIdenterBolkResultResponseProjection;
import no.nav.pdl.HentIdenterQueryRequest;
import no.nav.pdl.HentIdenterQueryResponse;
import no.nav.pdl.HentPersonQueryRequest;
import no.nav.pdl.HentPersonQueryResponse;
import no.nav.pdl.Identliste;
import no.nav.pdl.IdentlisteResponseProjection;
import no.nav.pdl.Person;
import no.nav.pdl.PersonResponseProjection;
import no.nav.vedtak.feil.Feil;
import no.nav.vedtak.feil.FeilFactory;
import no.nav.vedtak.feil.LogLevel;
import no.nav.vedtak.feil.deklarasjon.DeklarerteFeil;
import no.nav.vedtak.feil.deklarasjon.FunksjonellFeil;
import no.nav.vedtak.feil.deklarasjon.TekniskFeil;
import no.nav.vedtak.felles.integrasjon.rest.OidcRestClientResponseHandler;
import no.nav.vedtak.felles.integrasjon.rest.StsAccessTokenConfig;
import no.nav.vedtak.felles.integrasjon.rest.SystemConsumerStsRestClient;
import no.nav.vedtak.konfig.KonfigVerdi;

@Dependent
public class PdlKlient {
    public static final String PDL_KLIENT_NOT_FOUND_KODE = "F-399736";

    private static final String PDL_ERROR_CODE = "code";
    private static final String PDL_NOT_FOUND = "not_found";
    private static final String PDL_UNAUTHORIZED = "unauthorized";
    private static final String PDL_NOT_AUTHENTICATED = "unauthenticated";
    private static final String PDL_BAD_REQUEST = "bad_request";
    private static final String PDL_SERVER_ERROR = "server_error";

    private static final List<Integer> HTTP_KODER_TOM_RESPONS = List.of(
        HttpStatus.SC_NOT_MODIFIED,
        HttpStatus.SC_NO_CONTENT,
        HttpStatus.SC_ACCEPTED);

    private URI graphqlEndpoint;
    private CloseableHttpClient restKlient;
    private final ObjectMapper objectMapper = createObjectMapper();
    private final ObjectReader objectReaderHentPersonResponse = objectMapper.readerFor(HentPersonQueryResponse.class);
    private final ObjectReader objectReaderHentIdenterResponse = objectMapper.readerFor(HentIdenterQueryResponse.class);
    private final ObjectReader objectReaderHentIdenterBolkQueryResponse = objectMapper.readerFor(HentIdenterBolkQueryResponse.class);

    PdlKlient() {
        // CDI
    }

    PdlKlient(URI endpoint, SystemConsumerStsRestClient restKlient) {
        this.graphqlEndpoint = URI.create(endpoint.toString() + "/graphql");
        this.restKlient = restKlient;
    }

    @Inject
    public PdlKlient(@KonfigVerdi(value = "pdl.base.url", defaultVerdi = "https://localhost:8063/rest/api/pdl") URI endpoint,
                     StsAccessTokenConfig config) {
        this.graphqlEndpoint = URI.create(endpoint.toString() + "/graphql");
        this.restKlient = new SystemConsumerStsRestClient(config);
    }

    public Person hentPerson(HentPersonQueryRequest query, PersonResponseProjection projection, Tema consumerTemaKode) {
        GraphQLRequest graphQLRequest = new GraphQLRequest(query, projection);
        HentPersonQueryResponse graphQlResponse = utførSpørring(graphQLRequest, objectReaderHentPersonResponse, consumerTemaKode);
        return graphQlResponse.hentPerson();
    }

    public Identliste hentIdenter(HentIdenterQueryRequest query, IdentlisteResponseProjection projection, Tema consumerTemaKode) {
        GraphQLRequest graphQLRequest = new GraphQLRequest(query, projection);
        HentIdenterQueryResponse graphQlResponse = utførSpørring(graphQLRequest, objectReaderHentIdenterResponse, consumerTemaKode);
        return graphQlResponse.hentIdenter();
    }

    public List<HentIdenterBolkResult> hentIdenterBolkResults(HentIdenterBolkQueryRequest query, HentIdenterBolkResultResponseProjection projection, Tema consumerTemaKode) {
        //TODO Alle disse 3 metodene kan sikkert generaliseres
        GraphQLRequest graphQLRequest = new GraphQLRequest(query, projection);
        HentIdenterBolkQueryResponse graphQlResponse = utførSpørring(graphQLRequest, objectReaderHentIdenterBolkQueryResponse, consumerTemaKode);
        return graphQlResponse.hentIdenterBolk();
    }

    private <T extends GraphQLResult> T utførSpørring(GraphQLRequest request, ObjectReader objectReader, Tema consumerTemaKode) {
        var responseHandler = new OidcRestClientResponseHandler.ObjectReaderResponseHandler<T>(graphqlEndpoint, objectReader);

        T graphQlResponse;
        try {
            var httpPost = new HttpPost(graphqlEndpoint);
            httpPost.setEntity(new StringEntity(request.toHttpJsonBody()));
            httpPost.setHeader("TEMA", consumerTemaKode.name());
            graphQlResponse = utførForespørsel(httpPost, responseHandler);
        } catch (Exception e) {
            throw PdlTjenesteFeil.FEILFACTORY.pdlForespørselFeilet(request.toQueryString(), e).toException();
        }

        if (graphQlResponse.getErrors() != null && !graphQlResponse.getErrors().isEmpty()) {
            @SuppressWarnings("unchecked")
            var errors = (List<GraphQLError>) graphQlResponse.getErrors();
            if (errors.stream().anyMatch(PdlKlient::not_found)) {
                throw PdlTjenesteFeil.FEILFACTORY.personIkkeFunnet().toException();
            }
            var feilmelding = errors.stream()
                .map(GraphQLError::getMessage)
                .collect(Collectors.joining("\n Error: "));
            throw PdlTjenesteFeil.FEILFACTORY.forespørselReturnerteFeil(feilmelding).toException();
        }
        return graphQlResponse;
    }

    private <T extends GraphQLResult> T utførForespørsel(HttpPost request, OidcRestClientResponseHandler.ObjectReaderResponseHandler<T> responseHandler) throws IOException {
        try (var httpResponse = restKlient.execute(request)) {
            var responseCode = httpResponse.getStatusLine().getStatusCode();
            if (responseCode == HttpStatus.SC_OK) {
                return responseHandler.handleResponse(httpResponse);
            } else {
                var responseBody = HTTP_KODER_TOM_RESPONS.contains(responseCode)
                    ? "<tom_respons>"
                    : EntityUtils.toString(httpResponse.getEntity());
                var feilmelding = "Kunne ikke hente informasjon for query mot PDL: " + request.getURI()
                    + ", HTTP request=" + request.getEntity()
                    + ", HTTP status=" + httpResponse.getStatusLine()
                    + ". HTTP Errormessage=" + responseBody;
                //throw new SafException(feilmelding);
                throw new RuntimeException(feilmelding);
            }
        }
    }

    private static boolean not_found(GraphQLError error) {
        if (error == null || error.getExtensions() == null) {
            return false;
        }
        return PDL_NOT_FOUND.equals(error.getExtensions().get(PDL_ERROR_CODE));
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
            .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    interface PdlTjenesteFeil extends DeklarerteFeil { // NOSONAR - internt interface er ok her
        PdlTjenesteFeil FEILFACTORY = FeilFactory.create(PdlTjenesteFeil.class); // NOSONAR ok med konstant

        @TekniskFeil(feilkode = "F-539237", feilmelding = "Forespørsel til PDL feilet for spørring %s", logLevel = LogLevel.WARN)
        Feil pdlForespørselFeilet(String query, Throwable t);

        @TekniskFeil(feilkode = "F-399735", feilmelding = "Feil fra PDL ved utført query. Error: %s", logLevel = LogLevel.WARN)
        Feil forespørselReturnerteFeil(String response);

        @FunksjonellFeil(feilkode = PDL_KLIENT_NOT_FOUND_KODE, feilmelding = "Feil fra PDL ved utført query. Error: Person ikke funnet", løsningsforslag = "Slå opp gyldig ident", logLevel = LogLevel.WARN)
        Feil personIkkeFunnet();
    }
}
