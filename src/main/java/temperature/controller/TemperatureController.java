package temperature.controller;

import temperature.exception.*;
import temperature.model.*;
import temperature.startup.*;
import temperature.repository.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.hateoas.Link;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.Instant;

import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
public class TemperatureController {

        private final TemperatureRepository temperatureRepository;
        private final DeviceRepository deviceRepository;

        TemperatureController(TemperatureRepository temperatureRepository, DeviceRepository deviceRepository) {
                this.temperatureRepository = temperatureRepository;
                this.deviceRepository = deviceRepository;
        }

        @ApiOperation("Add a new temperature reading")
        @PostMapping("/temperatures")
        public Temperature newTemperature(@RequestBody Temperature temperatureRequest) {
                // Check if device is provided in the request
                if (temperatureRequest.getDevice() == null) {
                        throw new IllegalArgumentException("Device is required in the request body");
                }

                // Get the current user's API key
                String currentUserApiKey = getCurrentUserApiKey();

                // Check if device exists for the current user's API key
                Optional<Device> existingDevice = deviceRepository.findByDeviceNameAndApiKey(
                                temperatureRequest.getDevice().getDeviceName(), currentUserApiKey);

                Device device;
                if (existingDevice.isPresent()) {
                        device = existingDevice.get();
                } else {
                        // Check if the device name exists for any API key
                        Optional<Device> deviceWithSameName = deviceRepository
                                        .findByDeviceName(temperatureRequest.getDevice().getDeviceName());
                        if (deviceWithSameName.isPresent()) {
                                // Append a unique suffix to the device name
                                String uniqueDeviceName = appendUniqueSuffix(
                                                temperatureRequest.getDevice().getDeviceName());
                                // Create a new device associated with the current user's API key and unique
                                // device name
                                device = new Device(uniqueDeviceName, temperatureRequest.getDevice().getLocation(),
                                                currentUserApiKey);
                        } else {
                                // Create a new device associated with the current user's API key
                                device = new Device(temperatureRequest.getDevice().getDeviceName(),
                                                temperatureRequest.getDevice().getLocation(), currentUserApiKey);
                        }
                }

                // Save the device to the database
                deviceRepository.save(device);

                // Associate the device with the temperature
                temperatureRequest.setDevice(device);

                // Create a new Temperature object with the provided data
                Temperature newTemperature = new Temperature(temperatureRequest.getTemperatureC(),
                                temperatureRequest.getHumidityPercent(), System.currentTimeMillis(), device);

                // Calculate and set the dew point
                newTemperature.calculateAndSetDewPoint();

                // Save the temperature
                return temperatureRepository.save(newTemperature);
        }

        private String getCurrentUserApiKey() {
                // Retrieve the current authentication object
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof User) {
                        // Assuming UserDetails contains the API key information
                        User userDetails = (User) authentication.getPrincipal();
                        return userDetails.getApiKey();
                } else {
                        throw new RuntimeException("Unable to retrieve current user's API key");
                }
        }

        public String appendUniqueSuffix(String deviceName) {
                // Implement logic to generate a unique suffix and append it to the device name
                // For example, you can use a random UUID suffix
                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
                return deviceName + "_" + uniqueSuffix;
        }

        @ApiOperation("Get all temperatures")
        @GetMapping("/temperatures")
        CollectionModel<EntityModel<Temperature>> all() {
                String currentUserApiKey = getCurrentUserApiKey();

                List<EntityModel<Temperature>> temperatures = temperatureRepository.findAll().stream()
                                .filter(temperature -> temperature.getDevice().getApiKey().equals(currentUserApiKey))
                                .map(temperature -> {
                                        // Create a link to the temperature's details
                                        Link selfLink = linkTo(
                                                        methodOn(TemperatureController.class).one(temperature.getId()))
                                                        .withSelfRel();

                                        // Create a link to the device associated with this temperature
                                        Link deviceLink = linkTo(methodOn(DeviceController.class)
                                                        .one(temperature.getDevice().getId()))
                                                        .withRel("device");

                                        // Create an EntityModel for the temperature including links
                                        return EntityModel.of(temperature, selfLink, deviceLink);
                                })
                                .collect(Collectors.toList());

                // Return the collection model with links
                return CollectionModel.of(temperatures,
                                linkTo(methodOn(TemperatureController.class).all()).withSelfRel());
        }

        @ApiOperation("Get a temperature by ID")
        @GetMapping("/temperatures/{id}")
        EntityModel<Temperature> one(@ApiParam("Temperature ID") @PathVariable String id) {
                String currentUserApiKey = getCurrentUserApiKey();

                Temperature temperature = temperatureRepository.findById(id)
                                .filter(t -> t.getDevice().getApiKey().equals(currentUserApiKey))
                                .orElseThrow(() -> new TemperatureNotFoundException(id));

                // Create a link to itself
                Link selfLink = linkTo(methodOn(TemperatureController.class).one(id)).withSelfRel();

                // Create a link to retrieve all temperatures
                Link allLink = linkTo(methodOn(TemperatureController.class).all()).withRel("temperatures");

                // Create a link to the device associated with this temperature
                Link deviceLink = linkTo(methodOn(DeviceController.class).one(temperature.getDevice().getId()))
                                .withRel("device");

                // Create an EntityModel for the temperature including links
                return EntityModel.of(temperature, selfLink, allLink, deviceLink);
        }

        @ApiOperation("Delete a temperature by ID")
        @DeleteMapping("/temperatures/{id}")
        void deleteTemperature(@ApiParam("Temperature ID") @PathVariable String id) {
                // Check if the temperature exists
                if (temperatureRepository.existsById(id)) {
                        // Delete the temperature by ID
                        temperatureRepository.deleteById(id);
                } else {
                        throw new TemperatureNotFoundException(id);
                }
        }

        // Public Statistical Endpoints

        @ApiOperation("Get the latest temperature record")
        @GetMapping("/temperatures/latest")
        public ResponseEntity<EntityModel<Temperature>> getLatestTemperature() {
                String currentUserApiKey = getCurrentUserApiKey(); // Assuming you have a method to get the current
                                                                   // user's API key

                // Retrieve the latest temperature record associated with the current user's API
                // key
                Temperature latestTemperature = temperatureRepository
                                .findFirstByDevice_ApiKeyOrderByTimestampDesc(currentUserApiKey)
                                .orElseThrow(() -> new TemperatureNotFoundException(
                                                "No temperature records found for the current user"));

                // Format the timestamp into a human-readable string
                Instant instant = Instant.ofEpochMilli(latestTemperature.getTimestamp());
                String formattedTimestamp = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                // Create links for HATEOAS
                Link selfLink = linkTo(methodOn(TemperatureController.class).getLatestTemperature()).withSelfRel();
                Link temperatureLink = linkTo(methodOn(TemperatureController.class).one(latestTemperature.getId()))
                                .withRel("temperature");
                Link deviceLink = linkTo(methodOn(DeviceController.class).one(latestTemperature.getDevice().getId()))
                                .withRel("device");

                // Create an EntityModel for the latest temperature record including links
                EntityModel<Temperature> temperatureEntityModel = EntityModel.of(latestTemperature, selfLink,
                                temperatureLink,
                                deviceLink);

                // Add the formatted timestamp to the response
                temperatureEntityModel.add(new Link(formattedTimestamp, "formatted_timestamp"));

                // Return the response with HTTP status OK
                return ResponseEntity.ok(temperatureEntityModel);
        }

        @ApiOperation("Get all temperatures with filters")
        @GetMapping("/temperatures/filtered")
        public CollectionModel<EntityModel<Temperature>> allFiltered(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer day,
                        @RequestParam(required = false) Integer hour,
                        @RequestParam(required = false) Long startTimestamp,
                        @RequestParam(required = false) Long endTimestamp,
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String deviceId,
                        @RequestParam(required = false) String location) {

                List<Temperature> filteredTemperatures = filterTemperatures(year, month, day, hour, startTimestamp,
                                endTimestamp, deviceName, deviceId, location);

                List<EntityModel<Temperature>> temperatures = filteredTemperatures.stream()
                                .map(temperature -> EntityModel.of(temperature,
                                                linkTo(methodOn(TemperatureController.class).one(temperature.getId()))
                                                                .withSelfRel(),
                                                linkTo(methodOn(TemperatureController.class).allFiltered(year, month,
                                                                day, hour, startTimestamp,
                                                                endTimestamp, deviceName, deviceId, location))
                                                                .withRel("filteredTemperatures")))
                                .collect(Collectors.toList());

                return CollectionModel.of(temperatures,
                                linkTo(methodOn(TemperatureController.class)
                                                .allFiltered(year, month, day, hour, startTimestamp, endTimestamp,
                                                                deviceName, deviceId, location))
                                                .withSelfRel());
        }

        @ApiOperation("Get average temperatureC, humidity percentage, and dew point")
        @GetMapping("/temperatures/average")
        public ResponseEntity<Map<String, Object>> getAverage(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer day,
                        @RequestParam(required = false) Integer hour,
                        @RequestParam(required = false) Long startTimestamp,
                        @RequestParam(required = false) Long endTimestamp,
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String deviceId,
                        @RequestParam(required = false) String location) {

                List<Temperature> filteredTemperatures = filterTemperatures(year, month, day, hour, startTimestamp,
                                endTimestamp, deviceName, deviceId, location);

                // Calculate average temperatureC
                OptionalDouble averageTemperatureC = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getTemperatureC)
                                .average();

                // Calculate average humidity percentage
                OptionalDouble averageHumidityPercent = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getHumidityPercent)
                                .average();

                // Calculate average dew point
                OptionalDouble averageDewPoint = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getDewPoint)
                                .average();

                // Create the response map
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("averageTemperatureC", Math.round(averageTemperatureC.orElse(0.0) * 10.0) / 10.0);
                responseMap.put("averageHumidityPercent", Math.round(averageHumidityPercent.orElse(0.0) * 10.0) / 10.0);
                responseMap.put("averageDewPoint", Math.round(averageDewPoint.orElse(0.0) * 10.0) / 10.0);

                // Build self link with parameters if they exist
                UriComponentsBuilder uriBuilder = WebMvcLinkBuilder
                                .linkTo(methodOn(TemperatureController.class)
                                                .getAverage(year, month, day, hour, startTimestamp, endTimestamp,
                                                                deviceName, deviceId, location))
                                .toUriComponentsBuilder();
                String selfLink = uriBuilder.build().toUriString();

                // Add HATEOAS links in the response
                responseMap.put("self", selfLink);

                return ResponseEntity.ok(responseMap);
        }

        @ApiOperation("Get minimum temperatureC, humidity percentage, and dew point")
        @GetMapping("/temperatures/min")
        public ResponseEntity<Map<String, Object>> getMinimum(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer day,
                        @RequestParam(required = false) Integer hour,
                        @RequestParam(required = false) Long startTimestamp,
                        @RequestParam(required = false) Long endTimestamp,
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String deviceId,
                        @RequestParam(required = false) String location) {

                List<Temperature> filteredTemperatures = filterTemperatures(year, month, day, hour, startTimestamp,
                                endTimestamp, deviceName, deviceId, location);

                // Calculate minimum temperatureC
                OptionalDouble minTemperatureC = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getTemperatureC)
                                .min();

                // Calculate minimum humidity percentage
                OptionalDouble minHumidityPercent = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getHumidityPercent)
                                .min();

                // Calculate minimum dew point
                OptionalDouble minDewPoint = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getDewPoint)
                                .min();

                // Build JSON response
                Map<String, Object> minimums = new HashMap<>();
                minimums.put("minTemperatureC", minTemperatureC.orElse(0.0));
                minimums.put("minHumidityPercent", minHumidityPercent.orElse(0.0));
                minimums.put("minDewPoint", minDewPoint.orElse(0.0));

                // Build self link with parameters if they exist
                UriComponentsBuilder uriBuilder = WebMvcLinkBuilder
                                .linkTo(methodOn(TemperatureController.class)
                                                .getMinimum(year, month, day, hour, startTimestamp, endTimestamp,
                                                                deviceName, deviceId, location))
                                .toUriComponentsBuilder();
                String selfLink = uriBuilder.build().toUriString();

                // Include self link in the response
                minimums.put("self", selfLink);

                return ResponseEntity.ok(minimums);
        }

        @ApiOperation("Get maximum temperatureC, humidity percentage, and dew point")
        @GetMapping("/temperatures/max")
        public ResponseEntity<Map<String, Object>> getMaximum(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer day,
                        @RequestParam(required = false) Integer hour,
                        @RequestParam(required = false) Long startTimestamp,
                        @RequestParam(required = false) Long endTimestamp,
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String deviceId,
                        @RequestParam(required = false) String location) {

                List<Temperature> filteredTemperatures = filterTemperatures(year, month, day, hour, startTimestamp,
                                endTimestamp, deviceName, deviceId, location);

                // Calculate maximum temperatureC
                OptionalDouble maxTemperatureC = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getTemperatureC)
                                .max();

                // Calculate maximum humidity percentage
                OptionalDouble maxHumidityPercent = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getHumidityPercent)
                                .max();

                // Calculate maximum dew point
                OptionalDouble maxDewPoint = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getDewPoint)
                                .max();

                // Build JSON response
                Map<String, Object> maximums = new HashMap<>();
                maximums.put("maxTemperatureC", maxTemperatureC.orElse(0.0));
                maximums.put("maxHumidityPercent", maxHumidityPercent.orElse(0.0));
                maximums.put("maxDewPoint", maxDewPoint.orElse(0.0));

                // Build self link with parameters if they exist
                UriComponentsBuilder uriBuilder = WebMvcLinkBuilder
                                .linkTo(methodOn(TemperatureController.class)
                                                .getMaximum(year, month, day, hour, startTimestamp, endTimestamp,
                                                                deviceName, deviceId, location))
                                .toUriComponentsBuilder();
                String selfLink = uriBuilder.build().toUriString();

                // Include self link in the response
                maximums.put("self", selfLink);

                return ResponseEntity.ok(maximums);
        }

        @ApiOperation("Get median temperatureC, humidity percentage, and dew point")
        @GetMapping("/temperatures/median")
        public ResponseEntity<Map<String, Object>> getMedian(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer day,
                        @RequestParam(required = false) Integer hour,
                        @RequestParam(required = false) Long startTimestamp,
                        @RequestParam(required = false) Long endTimestamp,
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String deviceId,
                        @RequestParam(required = false) String location) {

                List<Temperature> filteredTemperatures = filterTemperatures(year, month, day, hour, startTimestamp,
                                endTimestamp, deviceName, deviceId, location);

                // Calculate median temperatureC
                OptionalDouble medianTemperatureC = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getTemperatureC)
                                .sorted()
                                .skip(filteredTemperatures.size() / 2)
                                .limit(1)
                                .findFirst();

                // Calculate median humidity percentage
                OptionalDouble medianHumidityPercent = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getHumidityPercent)
                                .sorted()
                                .skip(filteredTemperatures.size() / 2)
                                .limit(1)
                                .findFirst();

                // Calculate median dew point
                OptionalDouble medianDewPoint = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getDewPoint)
                                .sorted()
                                .skip(filteredTemperatures.size() / 2)
                                .limit(1)
                                .findFirst();

                // Build JSON response
                Map<String, Object> medians = new HashMap<>();
                medians.put("medianTemperatureC", medianTemperatureC.orElse(0.0));
                medians.put("medianHumidityPercent", medianHumidityPercent.orElse(0.0));
                medians.put("medianDewPoint", medianDewPoint.orElse(0.0));

                // Build self link with parameters if they exist
                UriComponentsBuilder uriBuilder = WebMvcLinkBuilder
                                .linkTo(methodOn(TemperatureController.class)
                                                .getMedian(year, month, day, hour, startTimestamp, endTimestamp,
                                                                deviceName, deviceId, location))
                                .toUriComponentsBuilder();
                String selfLink = uriBuilder.build().toUriString();

                // Include self link in the response
                medians.put("self", selfLink);

                return ResponseEntity.ok(medians);
        }

        // HELPERS

        private List<Temperature> filterTemperatures(Integer year, Integer month, Integer day, Integer hour,
                        Long startTimestamp, Long endTimestamp,
                        String deviceName, String deviceId, String location) {
                String currentUserApiKey = getCurrentUserApiKey();
                List<Temperature> allTemperatures = temperatureRepository.findAll();
                Stream<Temperature> temperatureStream = allTemperatures.stream();

                // Apply filters based on parameters
                temperatureStream = temperatureStream.filter(t -> t.getDevice().getApiKey().equals(currentUserApiKey));

                if (year != null)
                        temperatureStream = temperatureStream
                                        .filter(t -> getYearFromTimestamp(t.getTimestamp()) == year);
                if (month != null)
                        temperatureStream = temperatureStream
                                        .filter(t -> getMonthFromTimestamp(t.getTimestamp()) == month);
                if (day != null)
                        temperatureStream = temperatureStream.filter(t -> getDayFromTimestamp(t.getTimestamp()) == day);
                if (hour != null)
                        temperatureStream = temperatureStream
                                        .filter(t -> getHourFromTimestamp(t.getTimestamp()) == hour);
                if (deviceName != null)
                        temperatureStream = temperatureStream
                                        .filter(t -> t.getDevice().getDeviceName().equalsIgnoreCase(deviceName));
                if (deviceId != null)
                        temperatureStream = temperatureStream
                                        .filter(t -> t.getDevice().getId().equalsIgnoreCase(deviceId));
                if (location != null)
                        temperatureStream = temperatureStream
                                        .filter(t -> t.getDevice().getLocation().equalsIgnoreCase(location));

                // Filter temperatures between start and end timestamps
                if (startTimestamp != null && endTimestamp != null)
                        temperatureStream = temperatureStream.filter(
                                        t -> t.getTimestamp() >= startTimestamp && t.getTimestamp() <= endTimestamp);

                return temperatureStream.collect(Collectors.toList());
        }

        private int getYearFromTimestamp(long timestamp) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).getYear();
        }

        private int getMonthFromTimestamp(long timestamp) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).getMonthValue();
        }

        private int getDayFromTimestamp(long timestamp) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).getDayOfMonth();
        }

        private int getHourFromTimestamp(long timestamp) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).getHour();
        }

}