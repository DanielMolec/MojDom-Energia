# Mój Dom — Energia

Aplikacja Android do odczytu danych Home Assistant. Instrukcja konfiguracji: README.txt.

## Pobieranie APK

Actions → Build Android APK → zakończone uruchomienie → Artifacts → MojDom-Energia-APK. Rozpakuj ZIP i zainstaluj app-release.apk na telefonie (Android 8 lub nowszy).

Budowanie uruchamia się po zmianach na main lub ręcznie przez Run workflow.

## Aktualizacje

APK jest podpisywany stałym kluczem z GitHub Secrets, a każda kompilacja dostaje wyższy numer wersji (versionCode = 100 + numer uruchomienia). Nowy APK instaluje się więc jako aktualizacja — bez odinstalowania, z zachowaniem adresu, tokenu, wybranych czujników i pomieszczeń. Nie zmieniaj klucza w Secrets: inny klucz wymusza jednorazowe odinstalowanie.

Adres i token Home Assistant wpisuj w aplikacji na telefonie, nie w repozytorium. Odświeżanie co 5 sekund dotyczy odczytu istniejących stanów HA, nie wymusza nowych pomiarów kotła.
