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

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@RestController
public class TemperatureController {

        private final TemperatureRepository temperatureRepository;
        private final DeviceRepository deviceRepository;

        TemperatureController(TemperatureRepository temperatureRepository, DeviceRepository deviceRepository) {
                this.temperatureRepository = temperatureRepository;
                this.deviceRepository = deviceRepository;
        }

        @ApiOperation("Add a new temperature")
        @PostMapping("/temperatures")
        Temperature newTemperature(@RequestBody Temperature temperatureRequest) {
                // Check if device exists
                Optional<Device> existingDevice = deviceRepository
                                .findByDeviceName(temperatureRequest.getDevice().getDeviceName());

                Device device;
                if (existingDevice.isPresent()) {
                        device = existingDevice.get();
                        // Check if the location is different
                        if (!device.getLocation().equals(temperatureRequest.getDevice().getLocation())) {
                                // Update the location
                                device.setLocation(temperatureRequest.getDevice().getLocation());
                                // Save the updated device
                                device = deviceRepository.save(device);
                        }
                } else {
                        // If not, create a new device
                        device = deviceRepository.save(temperatureRequest.getDevice());
                }

                // Create a new Temperature object with the provided data and device
                Temperature newTemperature = new Temperature(temperatureRequest.getTemperatureC(),
                                temperatureRequest.getHumidityPercent(), System.currentTimeMillis(), device);

                // Save the temperature
                return temperatureRepository.save(newTemperature);
        }

        @ApiOperation("Get all temperatures")
        @GetMapping("/temperatures")
        CollectionModel<EntityModel<Temperature>> all() {
                List<EntityModel<Temperature>> temperatures = temperatureRepository.findAll().stream()
                                .map(temperature -> {
                                        // Create a link to the temperature's details
                                        Link selfLink = linkTo(
                                                        methodOn(TemperatureController.class).one(temperature.getId()))
                                                        .withSelfRel();

                                        // Create a link to all temperatures
                                        Link allLink = linkTo(methodOn(TemperatureController.class).all())
                                                        .withRel("temperatures");

                                        // Create a link to the device associated with this temperature
                                        Link deviceLink = linkTo(methodOn(DeviceController.class)
                                                        .one(temperature.getDevice().getId()))
                                                        .withRel("device");

                                        // Create an EntityModel for the temperature including links
                                        return EntityModel.of(temperature, selfLink, allLink, deviceLink);
                                })
                                .collect(Collectors.toList());

                // Return the collection model with links
                return CollectionModel.of(temperatures,
                                linkTo(methodOn(TemperatureController.class).all()).withSelfRel());
        }

        @ApiOperation("Get a temperature by ID")
        @GetMapping("/temperatures/{id}")
        EntityModel<Temperature> one(@ApiParam("Temperature ID") @PathVariable Long id) {
                Temperature temperature = temperatureRepository.findById(id)
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

        @ApiOperation("Update a temperature")
        @PutMapping("/temperatures/{id}")
        Temperature replaceTemperature(@RequestBody Temperature newTemperature, @PathVariable Long id) {
                return temperatureRepository.findById(id)
                                .map(temperature -> {
                                        // Update temperature data
                                        temperature.setTemperatureC(newTemperature.getTemperatureC());
                                        temperature.setHumidityPercent(newTemperature.getHumidityPercent());
                                        temperature.setTimestamp(newTemperature.getTimestamp());

                                        // Fetch or create the device
                                        Device device = deviceRepository
                                                        .findByDeviceName(newTemperature.getDevice().getDeviceName())
                                                        .orElse(deviceRepository.save(newTemperature.getDevice()));

                                        // Associate the temperature with the device
                                        temperature.setDevice(device);

                                        // Save and return the updated temperature
                                        return temperatureRepository.save(temperature);
                                })
                                .orElseGet(() -> {
                                        // If temperature with given id is not found, create a new temperature
                                        newTemperature.setId(id);

                                        // Fetch or create the device
                                        Device device = deviceRepository
                                                        .findByDeviceName(newTemperature.getDevice().getDeviceName())
                                                        .orElse(deviceRepository.save(newTemperature.getDevice()));

                                        // Associate the temperature with the device
                                        newTemperature.setDevice(device);

                                        // Save and return the new temperature
                                        return temperatureRepository.save(newTemperature);
                                });
        }

        @ApiOperation("Delete a temperature by ID")
        @DeleteMapping("/temperatures/{id}")
        void deleteTemperature(@ApiParam("Temperature ID") @PathVariable Long id) {
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
                // Retrieve the latest temperature record from the repository
                Temperature latestTemperature = temperatureRepository.findFirstByOrderByTimestampDesc()
                                .orElseThrow(() -> new TemperatureNotFoundException("No temperature records found"));

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
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String location) {

                List<Temperature> filteredTemperatures = filterTemperatures(year, month, day, hour, deviceName,
                                location);

                List<EntityModel<Temperature>> temperatures = filteredTemperatures.stream()
                                .map(temperature -> EntityModel.of(temperature,
                                                linkTo(methodOn(TemperatureController.class).one(temperature.getId()))
                                                                .withSelfRel(),
                                                linkTo(methodOn(TemperatureController.class).allFiltered(year, month,
                                                                day, hour, deviceName,
                                                                location)).withRel("filteredTemperatures")))
                                .collect(Collectors.toList());

                return CollectionModel.of(temperatures,
                                linkTo(methodOn(TemperatureController.class).allFiltered(year, month, day, hour,
                                                deviceName, location))
                                                .withSelfRel());
        }

        @ApiOperation("Get average temperatureC and humidity percentage")
        @GetMapping("/temperatures/average")
        public ResponseEntity<Map<String, Object>> getAverage(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer day,
                        @RequestParam(required = false) Integer hour,
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String location) {

                List<Temperature> filteredTemperatures = filterTemperatures(year, month, day, hour, deviceName,
                                location);

                // Calculate average temperatureC
                OptionalDouble averageTemperatureC = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getTemperatureC)
                                .average();

                // Calculate average humidity percentage
                OptionalDouble averageHumidityPercent = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getHumidityPercent)
                                .average();

                // Create the response map
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("averageTemperatureC", averageTemperatureC.orElse(0.0));
                responseMap.put("averageHumidityPercent", averageHumidityPercent.orElse(0.0));

                // Build self link with parameters if they exist
                UriComponentsBuilder uriBuilder = WebMvcLinkBuilder
                                .linkTo(methodOn(TemperatureController.class).getAverage(year, month, day, hour,
                                                deviceName, location))
                                .toUriComponentsBuilder();
                String selfLink = uriBuilder.build().toUriString();

                // Add HATEOAS links in the response
                responseMap.put("self", selfLink);

                return ResponseEntity.ok(responseMap);
        }

        @ApiOperation("Get minimum temperatureC and humidity percentage")
        @GetMapping("/temperatures/min")
        public ResponseEntity<Map<String, Object>> getMinimum(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer day,
                        @RequestParam(required = false) Integer hour,
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String location) {

                List<Temperature> filteredTemperatures = filterTemperatures(year, month, day, hour, deviceName,
                                location);

                // Calculate minimum temperatureC
                OptionalDouble minTemperatureC = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getTemperatureC)
                                .min();

                // Calculate minimum humidity percentage
                OptionalDouble minHumidityPercent = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getHumidityPercent)
                                .min();

                // Build JSON response
                Map<String, Object> minimums = new HashMap<>();
                minimums.put("minTemperatureC", minTemperatureC.orElse(0.0));
                minimums.put("minHumidityPercent", minHumidityPercent.orElse(0.0));

                // Build self link with parameters if they exist
                UriComponentsBuilder uriBuilder = WebMvcLinkBuilder
                                .linkTo(methodOn(TemperatureController.class).getMinimum(year, month, day, hour,
                                                deviceName, location))
                                .toUriComponentsBuilder();
                String selfLink = uriBuilder.build().toUriString();

                // Include self link in the response
                minimums.put("self", selfLink);

                return ResponseEntity.ok(minimums);
        }

        @ApiOperation("Get maximum temperatureC and humidity percentage")
        @GetMapping("/temperatures/max")
        public ResponseEntity<Map<String, Object>> getMaximum(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer day,
                        @RequestParam(required = false) Integer hour,
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String location) {

                List<Temperature> filteredTemperatures = filterTemperatures(year, month, day, hour, deviceName,
                                location);

                // Calculate maximum temperatureC
                OptionalDouble maxTemperatureC = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getTemperatureC)
                                .max();

                // Calculate maximum humidity percentage
                OptionalDouble maxHumidityPercent = filteredTemperatures.stream()
                                .mapToDouble(Temperature::getHumidityPercent)
                                .max();

                // Build JSON response
                Map<String, Object> maximums = new HashMap<>();
                maximums.put("maxTemperatureC", maxTemperatureC.orElse(0.0));
                maximums.put("maxHumidityPercent", maxHumidityPercent.orElse(0.0));

                // Build self link with parameters if they exist
                UriComponentsBuilder uriBuilder = WebMvcLinkBuilder
                                .linkTo(methodOn(TemperatureController.class).getMaximum(year, month, day, hour,
                                                deviceName, location))
                                .toUriComponentsBuilder();
                String selfLink = uriBuilder.build().toUriString();

                // Include self link in the response
                maximums.put("self", selfLink);

                return ResponseEntity.ok(maximums);
        }

        @ApiOperation("Get median temperatureC and humidity percentage")
        @GetMapping("/temperatures/median")
        public ResponseEntity<Map<String, Object>> getMedian(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer day,
                        @RequestParam(required = false) Integer hour,
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String location) {

                List<Temperature> filteredTemperatures = filterTemperatures(year, month, day, hour, deviceName,
                                location);

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

                // Build JSON response
                Map<String, Object> medians = new HashMap<>();
                medians.put("medianTemperatureC", medianTemperatureC.orElse(0.0));
                medians.put("medianHumidityPercent", medianHumidityPercent.orElse(0.0));

                // Build self link with parameters if they exist
                UriComponentsBuilder uriBuilder = WebMvcLinkBuilder
                                .linkTo(methodOn(TemperatureController.class).getMedian(year, month, day, hour,
                                                deviceName, location))
                                .toUriComponentsBuilder();
                String selfLink = uriBuilder.build().toUriString();

                // Include self link in the response
                medians.put("self", selfLink);

                return ResponseEntity.ok(medians);
        }

        // HELPERS

        private List<Temperature> filterTemperatures(Integer year, Integer month, Integer day, Integer hour,
                        String deviceName, String location) {
                List<Temperature> allTemperatures = temperatureRepository.findAll();
                Stream<Temperature> temperatureStream = allTemperatures.stream();

                // Apply filters based on parameters
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
                if (location != null)
                        temperatureStream = temperatureStream
                                        .filter(t -> t.getDevice().getLocation().equalsIgnoreCase(location));
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