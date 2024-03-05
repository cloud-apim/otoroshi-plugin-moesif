# Cloud APIM Moesif otoroshi plugin

## Prerequisites
In order to use the 'Moesif' plugin you have to enable the 'ApiKey' plugin on the route.

The 'Moesif' plugin will read the apikey informations using a json path.

Then, you need to link the customer_key and company_key fields to
a specific property of your apikey to specify which customer is using the route with his apikey.

This property could be located wherever you want in your apikey (in the metadata, in the tags, ...)

## Plugin configuration

`app_id` is an alphanumeric value field is **mandatory**

`customer_key` is a json path field is **mandatory**

`company_key` is a json path field is **mandatory**

`action_name` field is _optional_

Example

 ```json
{
    "app_id": "your moesif app id here", 
    "customer_key": "$.metadata.email",
    "company_key": "$.metadata.email",
    "action_name": "whatever you want" 
}
```

In this example, we linked the customer_key and the company_key to the email property located in the apikey's metadata `$.metadata.email`.
