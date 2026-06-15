-- Seed-data. INSERT OR IGNORE med eksplisitte id-er gjør kjøringen idempotent ved omstart.

INSERT OR IGNORE INTO destinations (id, name, country, description, price_per_night, available) VALUES
    (1, 'Lofoten Rorbuer',      'Norge',     'Tradisjonelle rorbuer med utsikt over fjorden.',        1850.0, 1),
    (2, 'Santorini Caldera',    'Hellas',    'Hvitkalkede suiter på kanten av vulkankrateret.',       2400.0, 1),
    (3, 'Kyoto Machiya',        'Japan',     'Historisk bytownhouse nær tempeldistriktet.',           1600.0, 1),
    (4, 'Toscana Agriturismo',  'Italia',    'Vingård og olivenlund i de toscanske åsene.',           1400.0, 1),
    (5, 'Tromsø Nordlys-lodge', 'Norge',     'Lodge med glasstak for nordlysobservasjon.',            2100.0, 1);

INSERT OR IGNORE INTO availability (id, destination_id, start_date, end_date, capacity, season_price) VALUES
    (1, 1, '2026-07-01', '2026-08-31', 6,  2200.0),
    (2, 1, '2026-09-01', '2026-10-31', 6,  NULL),
    (3, 2, '2026-06-01', '2026-09-30', 4,  2900.0),
    (4, 3, '2026-10-01', '2026-11-30', 3,  NULL),
    (5, 4, '2026-05-01', '2026-09-30', 8,  NULL),
    (6, 5, '2026-11-01', '2027-02-28', 4,  2600.0);
