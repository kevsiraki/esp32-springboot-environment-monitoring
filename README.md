# IoT Environmental Monitoring System

## Overview
The IoT Environmental Monitoring System is a comprehensive solution designed to monitor temperature and humidity levels remotely. It combines the capabilities of ESP32 microcontrollers with DHT11 sensors for data collection and a Spring Boot API for data processing and storage.

## Features
- **Real-time Monitoring:** Continuously monitors temperature and humidity levels and provides real-time data updates.
- **Data Storage:** Stores collected data securely for future analysis and reference.
- **RESTful API:** Provides a robust API for interacting with the system, enabling CRUD operations on devices and temperature records.
- **Authentication and Authorization:** Implements security mechanisms to ensure that only authorized users can access sensitive endpoints and perform privileged operations.
- **Error Handling:** Gracefully handles exceptions and provides feedback to users to maintain system stability.
- **Documentation:** Includes comprehensive API documentation for developers to explore and interact with the system effortlessly.

## Getting Started

### Setting Up the Spring Boot API
1. **Clone the Repository:** Clone this repository to your local machine.
2. **Configure `pom.xml`:** Open the `pom.xml` file and ensure that all dependencies are correctly configured. Make any necessary adjustments based on your project requirements.
3. **Run the API:** Run the Spring Boot API on your local machine or deploy it to a server. Refer to the API documentation for instructions on running and configuring the API.

### Configuring the ESP32 with Arduino IDE
1. **Install Arduino IDE:** Download and install the Arduino IDE from the [official website](https://www.arduino.cc/en/software).
2. **Install ESP32 Board Support:** Follow the instructions [here](https://github.com/espressif/arduino-esp32/blob/master/docs/arduino-ide/boards_manager.md) to install ESP32 board support in the Arduino IDE.
3. **Open Example Sketch:** Open the provided TemperatureClient directory to find the TemperatureClient.ino and secrets.example.h files.
4. **Configure Connectivity and Wi-Fi Credentials:** Modify the secrets.example.h definitions to include your Wi-Fi network SSID/password as well as your base API endpoint URL.
5. **Upload Sketch:** Connect your ESP32 device to your computer and upload the modified sketch to the device. Verify that the device successfully connects to the Wi-Fi network.

## Dependencies
- Arduino IDE
- ESP32 Boards Library
- Spring Boot
- MySQL Database
- Java JDK

## Documentation
For detailed documentation on the API endpoints and usage, refer to the [API Documentation](Documentation.pdf).

## Contributing
Contributions are welcome! If you have any suggestions, bug reports, or feature requests, please open an issue or submit a pull request.

## License
This project is licensed under the [MIT License](LICENSE).
