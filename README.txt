MÓJ DOM — ENERGIA / Android / wersja 1.0

STATUS
Projekt źródłowy natywnej aplikacji Java. NIE jest gotowym APK.
W środowisku przygotowania nie było Android SDK, więc nie wykonano kompilacji
Android ani testu na urządzeniu. Wymaga budowy i testu na telefonie.

CO ZAWIERA
- Gaz: chwilowy przepływ, dzienne i miesięczne zużycie szacowane, koszt miesięczny.
- Prąd: miesięczne zużycie, cena, koszt miesięczny i roczny z sensorów HA.
- Kocioł: palnik, modulacja, temperatury, ciśnienie, krzywa i taktowanie.
- Serwis: termin, dni do serwisu, status przypomnienia i stan techniczny.
- Dotknięcie karty pobiera historię encji z ostatnich 24 godzin.
- Odczyt API co 5 sekund, wyłącznie przy otwartej aplikacji. Jeden aktywny odczyt
  naraz. Gdy serwer odpowiada wolno, kolejne odczyty są pomijane.
- Edytowalne identyfikatory WSZYSTKICH encji. Domyślne nazwy pochodzą z przesłanej
  konfiguracji; brak encji nie jest prezentowany jako zerowe zużycie.
- Brak sterowania kotłem, reklam, analityki i powiadomień w tle.

BUDOWANIE NA WINDOWS
1. Zainstaluj Android Studio z oficjalnej strony developer.android.com/studio.
2. W SDK Manager zainstaluj Android SDK Platform 35 i Build-Tools 35.0.0.
3. Rozpakuj cały katalog projektu. W Android Studio wybierz Open i wskaż katalog
   zawierający settings.gradle. JDK do Gradle: 17 lub zgodny JBR z Android Studio.
4. Projekt używa Android Gradle Plugin 8.7.3 i Gradle 8.9; nie zawiera wrappera.
   Najprostsza budowa: otwórz PowerShell w katalogu i uruchom:
      powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
   Skrypt pobiera Gradle 8.9 z oficjalnego serwera, weryfikuje SHA256 i buduje APK.
   W razie pytania o licencje SDK zaakceptuj je w Android Studio SDK Manager.
   Można też skonfigurować lokalny Gradle 8.9 w Android Studio i wykonać assembleDebug.
5. APK będzie w app\build\outputs\apk\debug\app-debug.apk.
6. Skopiuj APK na telefon (Android 8.0 lub nowszy) i zainstaluj, zezwalając
   na instalację z użytego menedżera plików. Jest to wersja debug do prywatnego testu.
   Zachowaj klucz debug z komputera do późniejszych aktualizacji tej instalacji.

PIERWSZE URUCHOMIENIE
1. W Home Assistant na swoim profilu utwórz długoterminowy token dostępu.
   Użyj osobnego konta bez uprawnień administratora, jeśli wystarcza do odczytu.
   Token ma uprawnienia konta; sam klient aplikacji wykonuje tylko żądania GET.
2. W aplikacji wybierz Ustawienia. Podaj adres HA osiągalny z telefonu, np.
   https://twoj-ha.example lub lokalny http://192.168.x.x:8123.
3. Token wpisz NA TELEFONIE, nie wysyłaj go w czacie. Jest szyfrowany lokalnie
   kluczem Android Keystore. Kopie zapasowe danych aplikacji są wyłączone.
4. Dla HTTP trzeba osobno zaznaczyć zgodę: token jest wtedy przesyłany bez TLS.
   Preferuj HTTPS albo VPN do zaufanej sieci. Nie wyłączamy weryfikacji certyfikatów
   i nie podążamy za przekierowaniami z tokenem. Nie wystawiaj HA bezpośrednio
   do Internetu tylko po to, aby aplikacja działała poza domem.
5. Popraw nazwy encji, jeśli widzisz "Brak encji". Przycisk Wyloguj usuwa token
   z aplikacji; unieważnienie tokenu po stronie HA wykonuje się w profilu HA.

CO OZNACZAJĄ WARTOŚCI
Aplikacja wyświetla aktualne stany sensorów z HA — nie zastępuje importera Tauron
ani obliczeń gazu. Nie używa zewnętrznej statystyki tauron_importer:... bezpośrednio;
korzysta m.in. z sensor.koszt_energii_miesieczny, który już wskazywał 367,35 PLN.
Prąd jest dostępny z opóźnieniem zależnym od Taurona. Odczyt API co 5 sekund nie
wymusza odczytu kotła ani gazomierza: odpytywanie eBUS ustawiamy oddzielnie w HA.
Pole "Aktualizacja encji" pokazuje last_updated z HA, nie potwierdza świeżości
fizycznego pomiaru. Górny status informuje o czasie ostatniej odpowiedzi API.
W razie błędu pozostają poprzednie wartości z wyraźnym komunikatem braku nowych danych.
Historia wymaga integracji Recorder i dostępnych danych. Wykres pokazuje stan
licznika, nie słupki godzinowego zużycia; zerowanie licznika dziennego jest widoczne.
Terminy serwisu są tekstowe i nie mają wykresu liczbowego. Stany on/off są 1/0.

TEST NA TELEFONIE
- sprawdź poprawny i błędny token (401/403);
- porównaj odczyty gazu/prądu z HA dla tych samych encji;
- wyłącz Wi-Fi i sprawdź oznaczenie starych danych;
- schowaj aplikację: okresowe odczyty mają się zatrzymać;
- zmień identyfikator na nieistniejący: karta powinna pokazać brak encji;
- dotknij temperatury: sprawdź historię 24 h;
- wyloguj: stare dane powinny zniknąć.
