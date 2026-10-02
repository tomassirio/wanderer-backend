package com.tomassirio.wanderer.commons.domain;

import lombok.Getter;

@Getter
public enum AchievementType {
    // Activation achievements
    FIRST_TRIP("First Steps", "Create your first trip", 1),
    PROFILE_COMPLETED("All Set Up", "Add a display name, a bio and a profile photo", 1),

    // Distance achievements (in kilometers)
    DISTANCE_100KM("First Century", "Walk 100km in a single trip", 100),
    DISTANCE_200KM("4daagse", "Walk 200km in a single trip", 200),
    DISTANCE_500KM("Long Hauler", "Walk 500km in a single trip", 500),
    DISTANCE_800KM("Camino Complete", "Walk 800km in a single trip", 800),
    DISTANCE_1000KM("Thousand Miles", "Walk 1000km in a single trip", 1000),
    DISTANCE_1600KM("The Proclaimer", "Walk 500 miles and 500 more (~1600km)", 1600),
    DISTANCE_2200KM("The Hobbit", "Walk from the Shire to Mordor (2200km)", 2200),

    // Update achievements
    UPDATES_1("Breaking the Ice", "Post your first update on a trip", 1),
    UPDATES_10("Getting Started", "Post 10 updates on a single trip", 10),
    UPDATES_50("Regular Reporter", "Post 50 updates on a single trip", 50),
    UPDATES_100("Century Poster", "Post 100 updates on a single trip", 100),

    // Duration achievements (in days)
    DURATION_7_DAYS("Week Warrior", "Trip lasting 7 days", 7),
    DURATION_30_DAYS("Monthly Trekker", "Trip lasting 30 days", 30),
    DURATION_45_DAYS("Six Week Explorer", "Trip lasting 45 days", 45),
    DURATION_60_DAYS("Two Month Journey", "Trip lasting 60 days", 60),

    // Social achievements - Followers
    FOLLOWERS_1("First Fan", "Gain your first follower", 1),
    FOLLOWERS_10("Popular Walker", "Reach 10 followers", 10),
    FOLLOWERS_50("Influencer", "Reach 50 followers", 50),
    FOLLOWERS_100("Community Leader", "Reach 100 followers", 100),

    // Social achievements - Friends
    FRIENDS_1("First Friend", "Make your first friend", 1),
    FRIENDS_5("Making Friends", "Make 5 friends", 5),
    FRIENDS_20("Social Butterfly", "Make 20 friends", 20),
    FRIENDS_50("Friend Collector", "Make 50 friends", 50);

    private final String name;
    private final String description;
    private final int threshold;

    AchievementType(String name, String description, int threshold) {
        this.name = name;
        this.description = description;
        this.threshold = threshold;
    }
}
