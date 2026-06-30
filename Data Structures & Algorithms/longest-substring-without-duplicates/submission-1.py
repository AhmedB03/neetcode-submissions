class Solution:
    def lengthOfLongestSubstring(self, s: str) -> int:
        if not s:
            return 0
        
        sset = set()
        longest = 0
        l = 0

        for r in range(len(s)):
            if s[r] in sset:
                while s[r] in sset:
                    sset.remove(s[l])
                    l += 1
            sset.add(s[r])
            longest = max(longest,(r-l+1))
        
        return longest
            


