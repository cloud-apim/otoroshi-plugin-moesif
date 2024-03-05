# Cloud APIM Moesif otoroshi plugin

## Prerequisites
In order to use the 'Moesif' plugin you have to enable the `Apikeys` plugin on the route.

Firstly, go to your otoroshi UI.

Select your route(s) and add the `Apikeys` plugin.

The `Moesif` plugin will read the apikey informations using a json path.

Then, you need to link the customer_key and company_key fields to
a specific property of your apikey to specify which customer is using the route with his apikey.

This property could be located wherever you want in your apikey (in the metadata, in the tags, ...)

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

In this example, we linked the customer_key and the company_key to the email property located in the apikey's metadata `$.metadata.email`.

By default, the `Moesif` plugin won't send any information if all the mandatory fields are not filled.

## Customisation

You can customise the plugin on the routes you would like to cover.

You could add a filter on the routes which contain a billing property in the metatada.

In this example, we added a filter to cover all routes which have a billing property 
and the property's value is settled to `enabled`

You could also add some exlusions.

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