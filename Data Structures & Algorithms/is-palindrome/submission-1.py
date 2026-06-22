import re
class Solution:
    def isPalindrome(self, s: str) -> bool:
        s = re.sub(r"[^a-zA-Z0-9]", "", s)
        l,r = 0, len(s) -1
        s = s.lower()
        
        while r > l:
            if s[r] == s[l]:
                r -= 1
                l += 1
            else:
                return False
        
        return True