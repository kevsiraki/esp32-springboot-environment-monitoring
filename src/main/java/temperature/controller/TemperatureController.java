package temperature.controller;

import temperature.exception.*;
import temperature.model.*;
import temperature.startup.*;
import temperature.repository.*;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.data.redis.RedisConnectionFailureException;

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
import java.util.Collections;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
public class TemperatureController {

        private final TemperatureRepository temperatureRepository;
        private final DeviceRepository deviceRepository;

        TemperatureController(TemperatureRepository temperatureRepository, DeviceRepository deviceRepository) {
                this.temperatureRepository = temperatureRepository;
                this.deviceRepository = deviceRepository;
        }

        @Autowired
        private RedisTemplate<String, Object> redisTemplate;

        // General CRUD Endpoints

        @ApiOperation("Add a new temperature reading")
        @PostMapping("/temperatures")
        @CachePut(value = "temperatures", key = "#result.id")
        public Temperature newTemperature(@RequestBody Temperature temperatureRequest) {
                // Check if device is provided in the request
                if (temperatureRequest.getDevice() == null) {
                        throw new IllegalArgumentException("Device is required in the request body");
                }

                // Get the current user's API key
                String currentUserApiKey = getCurrentUserApiKey();

                // Find device by name and API key
                Optional<Device> existingDeviceOptional = deviceRepository.findByDeviceNameAndApiKey(
                                temperatureRequest.getDevice().getDeviceName(), currentUserApiKey);

                // Create or update the device
                Device device = existingDeviceOptional.orElse(new Device());

                // Set device properties
                device.setDeviceName(temperatureRequest.getDevice().getDeviceName());
                device.setApiKey(currentUserApiKey);

                // Check if the device location is provided and different from the existing
                // location
                if (temperatureRequest.getDevice().getLocation() != null &&
                                !Objects.equals(device.getLocation(), temperatureRequest.getDevice().getLocation())) {
                        device.setLocation(temperatureRequest.getDevice().getLocation());
                }

                // Save the device
                device = deviceRepository.save(device);

                // Associate the device with the temperature
                temperatureRequest.setDevice(device);

                // Generate UUID for the temperature
                temperatureRequest.setId(UUID.randomUUID().toString());

                // Create a new Temperature object with the provided data
                Temperature newTemperature = new Temperature(temperatureRequest.getTemperatureC(),
                                temperatureRequest.getHumidityPercent(), 0, device); // Setting timestamp to 0
                                                                                     // temporarily

                // Calculate and set the dew point
                newTemperature.calculateAndSetDewPoint();

                // Set the current timestamp
                newTemperature.setTimestamp(System.currentTimeMillis());

                // Save the temperature
                newTemperature = temperatureRepository.save(newTemperature);

                // Return the saved temperature
                return newTemperature;
        }

        @ApiOperation("Get a temperature by ID")
        @GetMapping("/temperatures/{id}")
        public EntityModel<Temperature> one(@ApiParam("Temperature ID") @PathVariable String id) {
                String currentUserApiKey = getCurrentUserApiKey();

                // Check if the data exists in Redis cache
                ValueOperations<String, Object> operations = redisTemplate.opsForValue();
                String cacheKey = "temperatures::" + id;
                boolean existsInCache;
                Temperature temperature;

                try {
                        existsInCache = redisTemplate.hasKey(cacheKey);

                        if (existsInCache) {
                                // Retrieve data from cache
                                temperature = (Temperature) operations.get(cacheKey);
                        } else {
                                // Retrieve data from database
                                temperature = temperatureRepository.findById(id)
                                                .filter(t -> t.getDevice().getApiKey().equals(currentUserApiKey))
                                                .orElseThrow(() -> new TemperatureNotFoundException(id));

                                // Store data in cache
                                operations.set(cacheKey, temperature);
                                redisTemplate.expire(cacheKey, 1, TimeUnit.HOURS); // Set expiration time (e.g., 1 hour)
                        }
                } catch (RedisConnectionFailureException e) {
                        temperature = temperatureRepository.findById(id)
                                        .filter(t -> t.getDevice().getApiKey().equals(currentUserApiKey))
                                        .orElseThrow(() -> new TemperatureNotFoundException(id));
                }

                // Create a link to itself
                Link selfLink = linkTo(methodOn(TemperatureController.class).one(id)).withSelfRel();

                // Create a link to retrieve all temperatures
                int defaultPage = 0; // or whatever default page number you want
                int defaultSize = 10; // or whatever default page size you want
                Link allLink = linkTo(methodOn(TemperatureController.class).all(defaultPage, defaultSize))
                                .withRel("temperatures");
                // Create a link to the device associated with this temperature
                Link deviceLink = linkTo(methodOn(DeviceController.class).one(temperature.getDevice().getId()))
                                .withRel("device");

                // Create an EntityModel for the temperature
                EntityModel<Temperature> entityModel = EntityModel.of(temperature, selfLink, allLink, deviceLink);

                return entityModel;
        }

        @ApiOperation("Get all temperatures with pagination")
        @GetMapping("/temperatures")
        public ResponseEntity<CollectionModel<EntityModel<Temperature>>> all(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {

                String currentUserApiKey = getCurrentUserApiKey();

                List<EntityModel<Temperature>> temperatures = new ArrayList<>();
                Page<Temperature> temperaturePage = null;

                try {
                        // Check if the page exists in the cache
                        String cachePageKey = "temperatures::page::" + page + "::size::" + size;
                        if (redisTemplate.hasKey(cachePageKey)) {
                                // If found in cache, retrieve it from cache
                                List<Temperature> cachedTemperatures = (List<Temperature>) redisTemplate.opsForValue()
                                                .get(cachePageKey);
                                for (Temperature temperature : cachedTemperatures) {
                                        temperatures.add(buildTemperatureEntityModel(temperature));
                                }
                        } else {
                                // If not found in cache, retrieve it from the database
                                temperaturePage = temperatureRepository.findAll(PageRequest.of(page, size));
                                List<Temperature> temperatureList = temperaturePage.getContent();
                                for (Temperature temperature : temperatureList) {
                                        temperatures.add(buildTemperatureEntityModel(temperature));
                                }
                                // Store page data in cache
                                redisTemplate.opsForValue().set(cachePageKey, temperatureList);
                                redisTemplate.expire(cachePageKey, 1, TimeUnit.HOURS);
                        }

                        // Add pagination links
                        CollectionModel<EntityModel<Temperature>> model = CollectionModel.of(temperatures,
                                        linkTo(methodOn(TemperatureController.class).all(page, size)).withSelfRel());

                        if (temperaturePage != null && temperaturePage.hasNext()) {
                                model.add(linkTo(methodOn(TemperatureController.class).all(page + 1, size))
                                                .withRel(IanaLinkRelations.NEXT));
                        }
                        if (temperaturePage != null && temperaturePage.hasPrevious()) {
                                model.add(linkTo(methodOn(TemperatureController.class).all(page - 1, size))
                                                .withRel(IanaLinkRelations.PREVIOUS));
                        }

                        return ResponseEntity.ok(model);

                } catch (RedisConnectionFailureException e) {
                        e.printStackTrace();

                        // If Redis is unavailable, retrieve data from the database directly
                        List<Temperature> temperatureList = temperatureRepository.findAll(PageRequest.of(page, size))
                                        .getContent();
                        for (Temperature temperature : temperatureList) {
                                if (temperature.getDevice().getApiKey().equals(currentUserApiKey)) {
                                        temperatures.add(buildTemperatureEntityModel(temperature));
                                }
                        }

                        // Return response without pagination since Redis is unavailable
                        CollectionModel<EntityModel<Temperature>> model = CollectionModel.of(temperatures);
                        return ResponseEntity.ok(model);
                }
        }

        @ApiOperation("Get all temperatures with filters")
        @GetMapping("/temperatures/filtered")
        public ResponseEntity<CollectionModel<EntityModel<Temperature>>> allFiltered(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer day,
                        @RequestParam(required = false) Integer hour,
                        @RequestParam(required = false) Long startTimestamp,
                        @RequestParam(required = false) Long endTimestamp,
                        @RequestParam(required = false) String deviceName,
                        @RequestParam(required = false) String deviceId,
                        @RequestParam(required = false) String location,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {

                List<Temperature> filteredTemperatures = null;

                try {
                        // Check if the filtered data exists in the cache
                        String cacheKey = constructCacheKey(year, month, day, hour, startTimestamp, endTimestamp,
                                        deviceName, deviceId, location, page, size);
                        if (redisTemplate.hasKey(cacheKey)) {
                                // If found in cache, retrieve it from cache
                                filteredTemperatures = (List<Temperature>) redisTemplate.opsForValue().get(cacheKey);
                        } else {
                                // If not found in cache, fetch from the database
                                filteredTemperatures = filterTemperatures(year, month, day, hour, startTimestamp,
                                                endTimestamp, deviceName, deviceId, location);
                                // Store filtered data in cache
                                redisTemplate.opsForValue().set(cacheKey, filteredTemperatures);
                                redisTemplate.expire(cacheKey, 1, TimeUnit.HOURS); // Cache expiration time
                        }
                } catch (Exception e) {
                        // Log the exception
                        e.printStackTrace();
                        // Fallback to fetching data from the database
                        filteredTemperatures = fetchFromDatabase(year, month, day, hour, startTimestamp, endTimestamp,
                                        deviceName, deviceId, location);
                }

                List<EntityModel<Temperature>> temperatures = paginateData(filteredTemperatures, page, size);

                // Add pagination links
                CollectionModel<EntityModel<Temperature>> model = constructModel(temperatures, year, month, day, hour,
                                startTimestamp, endTimestamp, deviceName, deviceId, location, page, size);
                return ResponseEntity.ok(model);
        }

        private String constructCacheKey(Integer year, Integer month, Integer day, Integer hour, Long startTimestamp,
                        Long endTimestamp, String deviceName, String deviceId, String location, int page, int size) {
                return String.format(
                                "temperatures::filtered::year::%d::month::%d::day::%d::hour::%d::startTimestamp::%d::endTimestamp::%d::deviceName::%s::deviceId::%s::location::%s::page::%d::size::%d",
                                year != null ? year : -1,
                                month != null ? month : -1,
                                day != null ? day : -1,
                                hour != null ? hour : -1,
                                startTimestamp != null ? startTimestamp : -1,
                                endTimestamp != null ? endTimestamp : -1,
                                deviceName != null ? deviceName : "null",
                                deviceId != null ? deviceId : "null",
                                location != null ? location : "null",
                                page, size);
        }

        private List<Temperature> fetchFromDatabase(Integer year, Integer month, Integer day, Integer hour,
                        Long startTimestamp, Long endTimestamp, String deviceName, String deviceId, String location) {
                return filterTemperatures(year, month, day, hour, startTimestamp, endTimestamp, deviceName, deviceId,
                                location);
        }

        private List<EntityModel<Temperature>> paginateData(List<Temperature> data, int page, int size) {
                int startIndex = page * size;
                int endIndex = Math.min(startIndex + size, data.size());
                List<Temperature> paginatedData = data.subList(startIndex, endIndex);

                List<EntityModel<Temperature>> paginatedModels = new ArrayList<>();
                for (Temperature temperature : paginatedData) {
                        paginatedModels.add(EntityModel.of(temperature,
                                        linkTo(methodOn(TemperatureController.class).one(temperature.getId()))
                                                        .withSelfRel(),
                                        linkTo(methodOn(TemperatureController.class).allFiltered(null, null, null, null,
                                                        null, null, null, null, null, page, size))
                                                        .withRel("filteredTemperatures")));
                }

                return paginatedModels;
        }

        private CollectionModel<EntityModel<Temperature>> constructModel(List<EntityModel<Temperature>> temperatures,
                        Integer year, Integer month, Integer day, Integer hour, Long startTimestamp, Long endTimestamp,
                        String deviceName, String deviceId, String location, int page, int size) {
                CollectionModel<EntityModel<Temperature>> model = CollectionModel.of(temperatures,
                                linkTo(methodOn(TemperatureController.class).allFiltered(year, month, day, hour,
                                                startTimestamp,
                                                endTimestamp, deviceName, deviceId, location, page, size))
                                                .withSelfRel());

                if (temperatures.size() > (page + 1) * size) {
                        model.add(linkTo(methodOn(TemperatureController.class).allFiltered(year, month, day, hour,
                                        startTimestamp,
                                        endTimestamp, deviceName, deviceId, location, page + 1, size))
                                        .withRel(IanaLinkRelations.NEXT));
                }
                if (page > 0) {
                        model.add(linkTo(methodOn(TemperatureController.class).allFiltered(year, month, day, hour,
                                        startTimestamp,
                                        endTimestamp, deviceName, deviceId, location, page - 1, size))
                                        .withRel(IanaLinkRelations.PREVIOUS));
                }
                return model;
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

        // Statistical Endpoints

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

        // Helper method to create EntityModel for temperature with necessary links
        private EntityModel<Temperature> buildTemperatureEntityModel(Temperature temperature) {
                // Create a link to the temperature's details
                Link selfLink = linkTo(methodOn(TemperatureController.class).one(temperature.getId())).withSelfRel();

                // Create a link to the device associated with this temperature
                Link deviceLink = linkTo(methodOn(DeviceController.class).one(temperature.getDevice().getId()))
                                .withRel("device");

                // Create an EntityModel for the temperature including links
                return EntityModel.of(temperature, selfLink, deviceLink);
        }

        // Helper method to add temperature to list if it matches the current user's API
        // key
        private void addTemperatureToListIfMatchingApiKey(Temperature temperature, String currentUserApiKey,
                        List<EntityModel<Temperature>> temperatures) {
                if (temperature.getDevice().getApiKey().equals(currentUserApiKey)) {
                        temperatures.add(buildTemperatureEntityModel(temperature));
                }
        }
}