#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <dht11.h>

#include "secrets.h"

#define DHT11PIN 4

#ifndef STASSID
#define STASSID SSID
#define STAPSK PASS
#endif

const char *ssid = STASSID;
const char *pass = STAPSK;

dht11 DHT11;

void initWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, pass);
  Serial.print("Connecting to WiFi ..");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print('.');
    delay(1000);
  }
  Serial.println(WiFi.localIP());
}

void setup() {
  Serial.begin(115200);
  initWiFi();
  Serial.print("RRSI: ");
  Serial.println(WiFi.RSSI());
}

void loop() {
  int chk = DHT11.read(DHT11PIN);
  // wait for WiFi connection
  if ((WiFi.status() == WL_CONNECTED)) {

    HTTPClient http;

    Serial.print("[HTTP] begin...\n");
    if (http.begin(API)) {  // HTTP
      http.addHeader("Content-Type", "application/json");

      Serial.print("[HTTP] POST...\n");
      // start connection and send HTTP header
      int httpCode = http.POST("{\"temperatureC\":" + String((float)DHT11.temperature, 1) + ", \"humidityPercent\":" + String((float)DHT11.humidity, 1) +  ", \"device\": {\"deviceName\":\"ESP32_1\", \"location\":\"Kevin Room\"}}");

      // httpCode will be negative on error
      if (httpCode > 0) {
        // HTTP header has been send and Server response header has been handled
        Serial.printf("[HTTP] POST... code: %d\n", httpCode);

        // file found at server
        if (httpCode == HTTP_CODE_OK) {
          String payload = http.getString();
          Serial.println(payload);
        }
      } else {
        Serial.printf("[HTTP] POST... failed, error: %s\n", http.errorToString(httpCode).c_str());
      }

      http.end();
    } else {
      Serial.printf("[HTTP} Unable to connect\n");
    }
  }

  delay(5 * 60 * 1000UL);
}
