# Mój Dom — Energia

Aplikacja Android do odczytu danych Home Assistant. Instrukcja konfiguracji: README.txt.

## Pobieranie APK

Actions → Build Android APK → zakończone uruchomienie → Artifacts → MojDom-Energia-APK. Rozpakuj ZIP i zainstaluj app-debug.apk na telefonie (Android 8 lub nowszy).

Budowanie uruchamia się po zmianach na main lub ręcznie przez Run workflow. Pierwsze uruchomienie musi zakończyć się sukcesem, zanim APK będzie dostępne.

To wersja testowa podpisywana automatycznie. Kolejne uruchomienie na nowym runnerze może użyć innego klucza: aktualizacja wtedy wymaga odinstalowania poprzedniej wersji i ponownej konfiguracji. Przed regularnymi aktualizacjami należy skonfigurować stały klucz podpisu.

Adres i token Home Assistant wpisuj w aplikacji na telefonie, nie w repozytorium. Odświeżanie co 5 sekund dotyczy odczytu istniejących stanów HA, nie wymusza nowych pomiarów kotła.
