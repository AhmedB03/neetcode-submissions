class Solution:
    def getSum(self, a: int, b: int) -> int:
        # 32-bit bitmask to bound numbers
        MASK = 0xFFFFFFFF
        # Max positive value for a 32-bit signed integer
        MAX_INT = 0x7FFFFFFF
        
        while b != 0:
            # XOR calculates the sum of bits without the carry
            # AND followed by left shift calculates the carry bits
            a, b = (a ^ b) & MASK, ((a & b) << 1) & MASK
        
        # If 'a' exceeds MAX_INT, it is a negative number in 32-bit two's complement
        return a if a <= MAX_INT else ~(a ^ MASK)

        return carry | a | b