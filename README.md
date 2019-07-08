# DataShare Plugin

The purpose of this addon is to retrieve structured data from Confluence pages.
To make this data consumable by third-party applications it is provided in JSON
format. The addon subscribes to page create/update/delete events. When a page is
saved the addon checks its contents for certain class names that identify objects
and attributes. It then parses the page to create JSON that it stores in
Confluence database.

The addon provides REST endpoints to third-party applications, system services
for integration with other Confluence plugins, CQL function to filter using
JSON data and other features described below. Please take a look at a 5-minute
introduction [video](https://youtu.be/Yq9is8wHqcU).
