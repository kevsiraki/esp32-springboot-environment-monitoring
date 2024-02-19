### Introduction
This API documentation provides details about the available endpoints, their operations, request parameters, and responses.

### Host
The API is hosted at `donttrip.org:8081`.

### User Management
#### Login
- **Description:** Log in to obtain the API key.
- **HTTP Method:** POST
- **Path:** `/api/login`
- **Request Body:**
  - `credentials`: User credentials.
- **Query Parameter:**
  - `regen`: Regenerate API key (optional, default: false).
- **Responses:**
  - `200 OK`: Successful login.
  - `201 Created`: Resource created.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

#### Refetch API Key
- **Description:** Refetch API key after login.
- **HTTP Method:** GET
- **Path:** `/api/refetchApiKey`
- **Responses:**
  - `200 OK`: Successful retrieval.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

#### Register User
- **Description:** Register a new user.
- **HTTP Method:** POST
- **Path:** `/api/register`
- **Request Body:**
  - `user`: User details.
- **Responses:**
  - `200 OK`: Successful registration.
  - `201 Created`: Resource created.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

### JWT Authentication Controller
#### Create Authentication Token
- **Description:** Create authentication token.
- **HTTP Method:** POST
- **Path:** `/authenticate`
- **Request Body:**
  - `authenticationRequest`: Authentication request details.
- **Responses:**
  - `200 OK`: Successful token creation.
  - `201 Created`: Resource created.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

### Device Controller
#### Get All Devices
- **Description:** Get all devices associated with the API key.
- **HTTP Method:** GET
- **Path:** `/devices`
- **Responses:**
  - `200 OK`: Successful retrieval.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

#### Get Device by ID
- **Description:** Get a device by ID associated with the API key.
- **HTTP Method:** GET
- **Path:** `/devices/{id}`
- **Path Parameter:**
  - `id`: Device ID.
- **Responses:**
  - `200 OK`: Successful retrieval.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

### Temperature Controller
#### Get All Temperatures
- **Description:** Get all temperatures.
- **HTTP Method:** GET
- **Path:** `/temperatures`
- **Responses:**
  - `200 OK`: Successful retrieval.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

#### Add New Temperature Reading
- **Description:** Add a new temperature reading.
- **HTTP Method:** POST
- **Path:** `/temperatures`
- **Request Body:**
  - `temperatureRequest`: Temperature reading details.
- **Responses:**
  - `200 OK`: Successful addition.
  - `201 Created`: Resource created.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

#### Get Average Temperature, Humidity, and Dew Point
- **Description:** Get average temperature, humidity percentage, and dew point.
- **HTTP Method:** GET
- **Path:** `/temperatures/average`
- **Query Parameters:**
  - `day`, `deviceId`, `deviceName`, `endTimestamp`, `hour`, `location`, `month`, `startTimestamp`, `year`: Filtering parameters.
- **Responses:**
  - `200 OK`: Successful retrieval.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

#### Get All Temperatures with Filters
- **Description:** Get all temperatures with filters.
- **HTTP Method:** GET
- **Path:** `/temperatures/filtered`
- **Query Parameters:**
  - `day`, `deviceId`, `deviceName`, `endTimestamp`, `hour`, `location`, `month`, `startTimestamp`, `year`: Filtering parameters.
- **Responses:**
  - `200 OK`: Successful retrieval.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

#### Get Latest Temperature Record
- **Description:** Get the latest temperature record.
- **HTTP Method:** GET
- **Path:** `/temperatures/latest`
- **Responses:**
  - `200 OK`

`: Successful retrieval.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

#### Get Maximum Temperature, Humidity, and Dew Point
- **Description:** Get maximum temperature, humidity percentage, and dew point.
- **HTTP Method:** GET
- **Path:** `/temperatures/max`
- **Query Parameters:**
  - `day`, `deviceId`, `deviceName`, `endTimestamp`, `hour`, `location`, `month`, `startTimestamp`, `year`: Filtering parameters.
- **Responses:**
  - `200 OK`: Successful retrieval.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

#### Get Median Temperature, Humidity, and Dew Point
- **Description:** Get median temperature, humidity percentage, and dew point.
- **HTTP Method:** GET
- **Path:** `/temperatures/median`
- **Query Parameters:**
  - `day`, `deviceId`, `deviceName`, `endTimestamp`, `hour`, `location`, `month`, `startTimestamp`, `year`: Filtering parameters.
- **Responses:**
  - `200 OK`: Successful retrieval.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

#### Get Minimum Temperature, Humidity, and Dew Point
- **Description:** Get minimum temperature, humidity percentage, and dew point.
- **HTTP Method:** GET
- **Path:** `/temperatures/min`
- **Query Parameters:**
  - `day`, `deviceId`, `deviceName`, `endTimestamp`, `hour`, `location`, `month`, `startTimestamp`, `year`: Filtering parameters.
- **Responses:**
  - `200 OK`: Successful retrieval.
  - `401 Unauthorized`: Authentication failure.
  - `403 Forbidden`: Access forbidden.
  - `404 Not Found`: Resource not found.

### Definitions
- **CollectionModel«EntityModel«Device»»**: Collection model containing entity models of devices.
- **CollectionModel«EntityModel«Temperature»»**: Collection model containing entity models of temperatures.
- **Device**: Represents a device with properties `apiKey`, `deviceName`, `id`, and `location`.
- **EntityModel«Device»**: Entity model representing a device with properties `apiKey`, `deviceName`, `id`, `location`, and links.
- **EntityModel«Temperature»**: Entity model representing a temperature reading with properties `device`, `dewPoint`, `humidityPercent`, `id`, `temperatureC`, `timestamp`, and links.
- **EntityModel«string»**: Entity model representing a string with links.
- **JwtRequest**: Represents a JWT authentication request with properties `username` and `password`.
- **Links**: Represents links with property `empty`.
- **Temperature**: Represents a temperature reading with properties `device`, `dewPoint`, `humidityPercent`, `id`, `temperatureC`, and `timestamp`.
- **User**: Represents a user with properties `apiKey`, `id`, `password`, and `username`.

This translation provides a detailed overview of the API endpoints, their functionalities, and the data structures used in the API.