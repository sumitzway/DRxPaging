package com.coderlab.cricketkotlindemo.paging.model

class Owner {
    var reputation: Int = 0
    var user_id: Long = 0
    var user_type: String? = null
    var profile_image: String? = null
    var display_name: String? = null
    var link: String? = null
}

class Item {
    var owner: Owner? = null
    var is_accepted: Boolean = false
    var score: Int = 0
    var last_activity_date: Long = 0
    var creation_date: Long = 0
    var answer_id: Long = 0
    var question_id: Long = 0
}

class StackApiResponse {
    var items: List<Item>? = null
    var has_more: Boolean = false
    var quota_max: Int = 0
    var quota_remaining: Int = 0
}