# User Registration, Login, and API Key Refresh

## User Registration

To register a new user, follow these steps:

- **Endpoint:** `/api/register`
- **HTTP Method:** POST
- **Request Body:** Provide user details including username, password, etc. in the request body as JSON.
```json
{
    "username": "your_username",
    "password": "your_password",
} 
```

-   Response: If the registration is successful, you'll receive a response with status code `200 OK`.

User Login and API Key Generation
---------------------------------

To log in and obtain an API key, follow these steps:

-   Endpoint: `/authenticate`
-   HTTP Method: POST
-   Request Body: Provide your credentials (username and password) in the request body as JSON.
```json
{
    "username": "your_username",
    "password": "your_password",
} 
```

-   Response: If the login is successful, you'll receive a response with status code `200 OK` along with the API key in the response body.

Adding API Key Header
---------------------

To access protected endpoints, you need to include your API key in the request headers.

### Example Usage:

-   Endpoint: `/devices`
-   HTTP Method: GET
-   Headers:
    -   `X-API-Key`: Include your API key obtained from the login request.


`GET /devices HTTP/1.1
Host: homeserver:8081
X-API-Key: your_api_key`

# User Login and API Key Generation

To log in and obtain an API key, you can use the following endpoint:

## Login Endpoint

- **Endpoint:** `/api/login`
- **HTTP Method:** POST
- **Description:** Login to get the API key. You can also regenerate the API key by setting the `regen` query parameter to `true`.
- **Request Body:** Provide your credentials (username and password) in the request body as JSON.
```json
{
    "username": "your_username",
    "password": "your_password",
} 
```

-   Query Parameter:
    -   `regen` (optional): Set this parameter to `true` if you want to regenerate the API key.
-   Response: If the login is successful, you'll receive a response with status code `200 OK` along with the API key in the response body.

### Example Usage:

`POST /api/login?regen=false HTTP/1.1
Content-Type: application/json`

```json
{
    "username": "your_username",
    "password": "your_password",
} 
```

### Regenerating API Key:

To regenerate the API key, set the `regen` query parameter to `true`. This will generate a new API key for the user.

`POST /api/login?regen=true HTTP/1.1
Content-Type: application/json`

```json
{
    "username": "your_username",
    "password": "your_password",
} 
```