package no.computas.vacationmcp.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Svaret fra {@link ReportingService#report(Long, LocalDate, LocalDate)} — en ferdig regnet
 * rapport over <b>omsetning</b> og <b>belegg</b>, med totalsummer på toppen og én linje per
 * reisemål, hver med én linje per tilgjengelighetsperiode.
 *
 * <p><b>Konvolutten er en del av definisjonen.</b> Et bart tall («omsetning: 41 200») er verdiløst
 * for en modell som skal forklare det videre: den vet ikke om kansellerte bookinger er med, om
 * datofilteret slo inn, eller hva nevneren i et belegg på 0,42 var. Derfor gjentar rapporten hva
 * det ble spurt om ({@code from}, {@code to}, {@code destinationId} — {@code null} betyr «ikke
 * filtrert»), og hver eneste rate leveres sammen med teller og nevner ({@code bookedNights} og
 * {@code capacityNights}), slik at tallet kan etterregnes uten flere verktøykall.
 *
 * @param from           datofilteret slik det ble oppgitt, eller {@code null} for «alle datoer»
 * @param to             datofilteret slik det ble oppgitt, eller {@code null} for «alle datoer»
 * @param destinationId  reisemålsfilteret slik det ble oppgitt, eller {@code null} for «alle»
 * @param destinations   antall linjer i {@code perDestination}
 * @param totals         summen av alle linjene
 * @param perDestination én linje per reisemål, sortert på omsetning (høyest først), deretter id
 */
public record BookingsReport(
        LocalDate from,
        LocalDate to,
        Long destinationId,
        int destinations,
        Totals totals,
        List<DestinationLine> perDestination) {

    /**
     * Tallene som defineres likt på alle nivåer i rapporten (totalt og per reisemål), slik at et
     * felt betyr det samme uansett hvor modellen leser det.
     *
     * <h2>Omsetning</h2>
     *
     * <p>{@code revenue} er summen av {@code totalPrice} for bookinger som teller med, og «teller
     * med» er <b>alle statuser unntatt {@code CANCELLED}</b> — nøyaktig samme sett som holder på
     * kapasiteten i T-11. En kansellert booking gir hverken inntekt eller beleggsplass, så den
     * ville forurenset begge tallene. {@code PENDING} er derimot <em>med</em>: plassene er
     * beslaglagt for alle andre, og et reisemål som er utsolgt av ubekreftede bookinger er ikke
     * ledig. Fordi det likevel er forskjell på penger som er lovet og penger som er betalt, skilles
     * den usikre delen ut i {@code pendingRevenue} (en delmengde av {@code revenue}, ikke et
     * tillegg), og det som ble avlyst rapporteres for seg i {@code cancelledRevenue} — utenfor
     * {@code revenue}, men synlig, så modellen kan svare på «hvor mye mistet vi på avbestillinger?».
     *
     * <h2>Belegg</h2>
     *
     * <p>{@code occupancyRate} er {@code bookedNights / capacityNights} — <b>plassdøgn</b>, ikke
     * antall bookinger. {@code capacityNights} er periodens {@code capacity} (antall samtidige
     * plasser, jf. T-11) ganget med antall netter i perioden, og {@code bookedNights} er summen av
     * {@code numTravelers × overlappende netter} for de bookingene som teller med. Å måle i
     * plassdøgn er det eneste som gir et tall mellom 0 og 1: en periode på to måneder med kapasitet
     * 3 tar imot langt flere enn 3 reisende i løpet av perioden, så «reisende / capacity» ville gitt
     * 400 % uten at noe var overbooket. Kapasitetsregelen i T-11 er konservativ og slipper aldri
     * gjennom en enkeltdag med flere reisende enn {@code capacity}, så
     * {@code bookedNights ≤ capacityNights} — belegget kan ikke overstige 100 %.
     *
     * <p>{@code occupancyRate} er en brøk mellom 0 og 1 med fire desimaler (0.4167 = 41,67 %), og
     * er <b>{@code null}</b> når {@code capacityNights} er 0: da finnes det ingen åpen periode å
     * måle mot, og et belegg på «0 %» ville løyet om at det var ledig plass ingen tok.
     *
     * @param bookings          antall bookinger som teller med (alt unntatt {@code CANCELLED})
     * @param travelers         antall reisende i dem — hoder, ikke plassdøgn
     * @param revenue           sum {@code totalPrice} i kroner for de samme bookingene
     * @param pendingRevenue    den delen av {@code revenue} som står i {@code PENDING}
     * @param cancelledBookings antall kansellerte bookinger (utenfor tallene over)
     * @param cancelledRevenue  sum {@code totalPrice} for dem, i kroner (utenfor {@code revenue})
     * @param capacityNights    nevneren i belegget: Σ {@code capacity × netter} over periodene
     * @param bookedNights      telleren: Σ {@code numTravelers × overlappende netter}
     * @param occupancyRate     {@code bookedNights / capacityNights}, eller {@code null}
     */
    public record Totals(
            int bookings,
            int travelers,
            double revenue,
            double pendingRevenue,
            int cancelledBookings,
            double cancelledRevenue,
            int capacityNights,
            int bookedNights,
            Double occupancyRate) {
    }

    /**
     * Én linje per reisemål. Navn og land er med for at rapporten skal stå på egne bein — uten dem
     * måtte modellen kalt {@code list_destinations} bare for å kunne si hva «reisemål 3» heter.
     *
     * @param totals  samme felt og samme definisjoner som på toppnivå, avgrenset til dette reisemålet
     * @param periods én linje per tilgjengelighetsperiode, sortert på startdato
     */
    public record DestinationLine(
            long destinationId,
            String name,
            String country,
            Totals totals,
            List<PeriodLine> periods) {
    }

    /**
     * Én linje per tilgjengelighetsperiode — det er her belegget faktisk regnes ut, og
     * reisemålslinja er bare summen av dem.
     *
     * <p>{@code startDate} og {@code endDate} er perioden <b>slik den ble talt med</b>, altså
     * klippet mot et eventuelt {@code from}/{@code to}-filter. Uten filter er de identiske med
     * periodens egne datoer. {@code availabilityId} peker tilbake på hele perioden, slik den vises
     * av {@code check_availability}.
     *
     * <p>{@code bookings} og {@code revenue} er de bookingene som <em>hører hjemme</em> i perioden
     * (den ene raden som dekker hele oppholdet, jf. {@code PricingService.findCoveringPeriod}),
     * mens {@code bookedNights} teller alle plassdøgn som faller innenfor datointervallet. De to
     * spørsmålene er ulike, og for en booking som ligger i perioden er svaret det samme.
     */
    public record PeriodLine(
            long availabilityId,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int nights,
            int capacityNights,
            int bookedNights,
            Double occupancyRate,
            int bookings,
            double revenue) {
    }
}
