# Cloud APIM Moesif otoroshi plugin

## Prerequisites

In order to use the `Moesif` plugin you have to enable the `Apikeys` plugin on your route(s) you will use.

Firstly, go to your otoroshi UI.

Select your route(s) and add the `Apikeys` plugin.

The `Moesif` plugin will read the apikey information using a json path.

Then, you need to link the `customer_key` and `company_key` fields to
a specific property of your apikey to specify which customer is using the route with his apikey.

This property could be located wherever you want in your apikey (in the metadata, in the tags, ...)

As an example you could put the `customer_key`and the `company_key`in the apikey metadata.

```json
{
  "_loc": {
    "tenant": "default",
    "teams": [
      "default"
    ]
  },
  "clientId": "YOUR_CLIENT_ID_HERE",
  "clientSecret": "YOUR_CLIENT_SECRET_HERE",
  "clientName": "CLIENT_NAME",
  "description": "",
  "authorizedGroup": "default",
  "authorizedEntities": [
    "ROUTE_HERE",
    "group_default"
  ],
  "authorizations": [
    {
      "kind": "service",
      "id": "ROUTE_HERE"
    },
    {
      "kind": "group",
      "id": "default"
    }
  ],
  "enabled": true,
  "readOnly": false,
  "allowClientIdOnly": false,
  "throttlingQuota": 10000000,
  "dailyQuota": 10000000,
  "monthlyQuota": 10000000,
  "constrainedServicesOnly": false,
  "restrictions": {
    "enabled": false,
    "allowLast": true,
    "allowed": [],
    "forbidden": [],
    "notFound": []
  },
  "rotation": {
    "enabled": false,
    "rotationEvery": 744,
    "gracePeriod": 168,
    "nextSecret": null
  },
  "validUntil": null,
  "tags": [],
  "metadata": {
    "customer_id": "YOUR CUSTOMER ID HERE",
    "company_id": "YOUR CUSTOMER COMPANY HERE"
  },
  "kind": "ApiKey"
}
```

## Add the plugin to otoroshi

Go to the `Data Exporters` tab and press the `Add Item` button.

Choose `custom` as data exporter type.

In the `Exporter Config` panel you need to select `Moesif`.

Then, you need to configure the plugin as explain in the next chapter.

## Plugin configuration

`app_id` : `String` field is **mandatory**

`customer_key` : `JSON path` field is **mandatory**

`company_key` : `JSON path` field is **mandatory**

`action_name` : `String` field is _optional_

### Example

 ```json
{
  "app_id": "your moesif app id here",
  "customer_key": "$.metadata.email",
  "company_key": "$.metadata.email",
  "action_name": "whatever you want"
}
```

In this example, we linked the customer_key and the company_key to the email property located in the apikey's
metadata `$.metadata.email`.

By default, the `Moesif` plugin won't send any information if all the mandatory fields are not filled.

## Customisation

You can customise the plugin on the routes you would like to cover.

You could add a filter on the routes which contain a billing property in the metatada.

In this example, we added a filter to cover all routes which have a billing property
and the property's value is settled to `enabled`

```json
{
  "include": [
    {
      "@type": "GatewayEvent",
      "route": {
        "metadata": {
          "billing": "enabled"
        }
      }
    }
  ],
  "exclude": []
}
```

You could also add some exlusions based on your route metadata, tags, etc...

```json
{
  "include": [],
  "exclude": [
    {
      "@type": "GatewayEvent",
      "route": {
        "metadata": {
          "billing": "disabled"
        }
      }
    }
  ]
}
```

Or add some exlusions based on the host target of your route(s).

```json
{
  "include": [],
  "exclude": [
    {
      "@type": "GatewayEvent",
      "target": {
        "host": "myapp.mydomain.com"
      }
    }
  ]
}
```