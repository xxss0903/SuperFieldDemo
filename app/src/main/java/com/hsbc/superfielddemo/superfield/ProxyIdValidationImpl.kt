package com.hsbc.superfielddemo.superfield

import com.hsbc.superfielddemo.superfield.IValidationStrategy

/**
 * Created by zack zeng on 2018/5/15.
 */
class ProxyIdValidationImpl(strategy: IValidationStrategy) {
    private var strategy: IValidationStrategy

    init {
        this.strategy = strategy
    }

    fun contextInterface(input: String) {
        this.strategy.validProxyId(input)
    }

}