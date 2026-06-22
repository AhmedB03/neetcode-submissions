import heapq as hq
class Solution:
    def topKFrequent(self, nums: List[int], k: int) -> List[int]:
        freq = {}
        res = []

        for i in nums:
            freq[i] = 1 + freq.get(i,0)
        
        lis = list(freq.items())
        hq.heapify(lis)
     
      
        sorted_list = sorted(lis, key=lambda x: x[1], reverse=True)
        print(sorted_list)

        for i in sorted_list:
            res.append(i[0])

        return res[:k]