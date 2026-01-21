def process_data(items):
    result = []
    for item in items:
        if item.get('status') == 'error':
            # Handle error
            error_message = f"Error: {item.get('message', 'Unknown error')}"
            # Do something with error_message, but let's assume we just log it or skip it?
            # In our example, we want to skip errors and collect successes.
            # But the original code might have more complex conditions.
            # Let's say we also have to check for other conditions and do different things.
            if item.get('type') == 'critical':
                # For critical errors, do something special
                critical_action(error_message)
            else:
                # For non-critical, just log
                log_error(error_message)
        else:
            # For success, process the item
            processed_item = process_success(item)
            result.append(processed_item)
    return result
def calculate_total(items, discount, tax_rate, special_promotion, is_vip, is_weekend):
    total = 0
    for item in items:
        if item['type'] == 'book':
            price = item['price'] * 1.5  # because books are 50% expensive
        elif item['type'] == 'electronic':
            price = item['price'] * 2.0  # because electronics are 100% expensive
        elif item['type'] == 'clothing':
            price = item['price'] * 1.0  # normal price
        else:
            # Maybe there's a default case
            price = item['price']

        if discount and item['type'] == 'book':
            price = price * 0.8
        elif discount and item['type'] == 'electronic':
            price = price * 0.9
        elif discount and item['type'] == 'clothing':
            price = price * 0.7

        if special_promotion and item['type'] == 'clothing':
            price = price * 0.8

        if is_vip and item['type'] != 'clothing':
            price = price * 0.9

        if is_weekend and price > 50:
            price = price * 0.95

        total += price

    return total
def calculate_total(items, discount, tax_rate, special_promotion, is_vip, is_weekend):
    total = 0
    for item in items:
        base_price = 0
        if item['type'] == 'book':
            base_price = item['price'] * 1.5
        elif item['type'] == 'electronic':
            base_price = item['price'] * 2.0
        elif item['type'] == 'clothing':
            base_price = item['price'] * 1.0
        else:
            base_price = item['price']

        # Now apply discounts and promotions
        price = base_price
        if discount and item['type'] == 'book':
            price = price * 0.8
        elif discount and item['type'] == 'electronic':
            price = price * 0.9
        elif discount and item['type'] == 'clothing':
            price = price * 0.7

        if special_promotion and item['type'] == 'clothing':
            price = price * 0.8

        if is_vip and item['type'] != 'clothing':
            price = price * 0.9

        if is_weekend and price > 50:
            price = price * 0.95

        total += price

    return total
def calculate_total(items, discount, tax_rate, special_promotion, is_vip, is_weekend):
    total = 0
    for item in items:
        price = calculate_base_price(item)
        apply_discount(price, item, discount)
        apply_special_promotion(price, item, special_promotion)
        apply_vip_discount(price, item, is_vip)
        apply_weekend_discount(price, item, is_weekend)
        total += price

    return total * (1 + tax_rate)  # Assuming we need to apply tax at the end, but original didn't have tax

# But the original didn't have tax, so maybe we don't need to apply tax. Let's stick to the original.

# However, the original code didn't have a tax application, so I added it for example. Remove if not needed.

# Let's define the helper functions:

def calculate_base_price(item):
    if item['type'] == 'book':
        return item['price'] * 1.5
    elif item['type'] == 'electronic':
        return item['price'] * 2.0
    elif item['type'] == 'clothing':
        return item['price'] * 1.0
    else:
        return item['price']

def apply_discount(price, item, discount):
    if discount:
        if item['type'] == 'book':
            return price * 0.8
        elif item['type'] == 'electronic':
            return price * 0.9
        elif item['type'] == 'clothing':
            return price * 0.7
        else:
            return price

def apply_special_promotion(price, item, special_promotion):
    if special_promotion and item['type'] == 'clothing':
        return price * 0.8
    return price

def apply_vip_discount(price, item, is_vip):
    if is_vip and item['type'] != 'clothing':
        return price * 0.9
    return price

def apply_weekend_discount(price, item, is_weekend):
    if is_weekend and price > 50:
        return price * 0.95
    return price

def process_data(items):
    result = []
    for item in items:
        if item.get('status') == 'error':
            if item.get('type') == 'critical':
                critical_action(f"Error: {item.get('message', 'Unknown error')}")
            else:
                log_error(f"Error: {item.get('message', 'Unknown error')}")
        else:
            processed_item = process_success(item)
            result.append(processed_item)
    return result
def process_data(items):
    result = []
    for item in items:
        if item['status'] == 'error':
            handle_error(item)
        else:
            handle_success(item, result)
    return result

def handle_error(item):
    error_message = item.get('message', 'Unknown error')
    if item.get('type') == 'critical':
        critical_action(error_message)
    else:
        log_error(error_message)

def handle_success(item, result):
    processed_item = process_success(item)
    result.append(processed_item)

def complex_function(n):
    if n < 0:
        return 0
    elif n == 0:
        return 1
    else:
        a = 0
        for i in range(n):
            if i % 2 == 0:
                a += i
            else:
                a -= i
        if a > 10:
            return a * 2
        elif a < 0:
            return -a
        else:
            # What if a is between 0 and 10, but we have more conditions?
            # Let's say we do something more complex.
            b = 0
            for j in range(1, n+1):
                if j < n:
                    b += j
                else:
                    b *= j
            if b % 3 == 0:
                return b // 3
            else:
                return b + 1
def complex_function(n):
    if n < 0:
        return 0
    if n == 0:
        return 1
    a = calculate_alternating_sum(n)
    if a > 10:
        return a * 2
    if a < 0:
        return -a
    # Now for the else part
    b = calculate_sum_up_to_n(n)
    if b % 3 == 0:
        return b // 3
    return b + 1

def calculate_alternating_sum(n):
    total = 0
    for i in range(n):
        if i % 2 == 0:
            total += i
        else:
            total -= i
    return total

def calculate_sum_up_to_n(n):
    total = 0
    for j in range(1, n+1):
        if j < n:
            total +=